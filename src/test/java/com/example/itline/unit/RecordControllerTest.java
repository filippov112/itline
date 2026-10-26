package com.example.itline.unit;

import com.example.itline.domain.Record;
import com.example.itline.service.ConflictException;
import com.example.itline.service.NotFoundException;
import com.example.itline.service.RecordService;
import com.example.itline.web.RecordController;
import com.example.itline.web.dto.RecordDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты web-слоя: маршрутизация, JSON, статусы, обработка исключений.
 *
 * @WebMvcTest поднимает только контроллер + @RestControllerAdvice + Jackson + Validator.
 * RecordService замокан — его логика проверяется в RecordServiceTest.
 * БД нет — реальный @Version проверяется в OptimisticLockingIT.
 */
@WebMvcTest(RecordController.class)
class RecordControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RecordService service;

    // Помощник: собрать сущность Record для мока сервиса.
    // id/version в реальности ставит JPA; для теста — через рефлексию.
    private static Record record(long id, long version, String name) {
        Record r = new Record(false, "icon1", name, "desc");
        TestRecords.setId(r, id);
        TestRecords.setVersion(r, version);
        return r;
    }

    // ======================= GET /api/records =======================

    @Test
    @DisplayName("GET /api/records -> 200 и массив записей")
    void list_returns200AndArray() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                record(1L, 0L, "Первая"),
                record(2L, 5L, "Вторая")
        ));

        mvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Первая"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].icon").value("icon1"))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].version").value(5));

        verify(service).findAll();
    }

    @Test
    @DisplayName("GET /api/records на пустой БД -> 200 и []")
    void list_empty() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ======================= POST /api/records =======================

    @Test
    @DisplayName("POST с валидным телом -> 201 и созданная запись")
    void create_returns201() throws Exception {
        when(service.create(any())).thenReturn(record(42L, 0L, "Новая"));

        String body = """
                {
                "active": true,
                "icon": "icon2",
                "name": "Новая",
                "description": "Описание"
                }
                """;

        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())          // 201, не 200
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Новая"))
                .andExpect(jsonPath("$.version").value(0));

        verify(service).create(any());
    }

    // ======================= POST /api/records | 400 =======================

    @Test
    @DisplayName("POST с пустым name -> 400, сервис не вызывается")
    void create_blankName_returns400() throws Exception {
        String body = """
                {"active": true, "icon": "icon1", "name": "  ", "description": "x"}
                """;

        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.name").exists());

        verifyNoInteractions(service);   // до сервиса не дошли
    }

    @Test
    @DisplayName("POST с недопустимой icon -> 400")
    void create_badIcon_returns400() throws Exception {
        String body = """
                {"active": true, "icon": "icon9", "name": "X", "description": "Y"}
                """;

        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.icon").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST с пустым description -> 400")
    void create_blankDescription_returns400() throws Exception {
        String body = """
                {"active": true, "icon": "icon1", "name": "X", "description": ""}
                """;

        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.description").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST с отсутствующей icon -> 400")
    void create_missingIcon_returns400() throws Exception {
        String body = """
                {"active": true, "name": "X", "description": "Y"}
                """;

        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.icon").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST с битым JSON -> 400")
    void create_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // ======================= POST /api/records | active отсутствует в JSON =======================

    @Test
    @DisplayName("POST без active -> active=false по умолчанию")
    void create_missingActive_defaultsFalse() throws Exception {
        when(service.create(any())).thenAnswer(inv -> {
            var req = (com.example.itline.web.dto.CreateRecordRequest) inv.getArgument(0);
            // проверяем, что контроллер передал в сервис именно false
            org.assertj.core.api.Assertions.assertThat(req.active()).isFalse();
            return record(1L, 0L, req.name());
        });

        String body = """
                {"icon": "icon1", "name": "X", "description": "Y"}
                """;

        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ======================= PUT /api/records/{id} =======================

    @Test
    @DisplayName("PUT с корректной version -> 200 и обновлённая запись")
    void update_returns200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(record(1L, 4L, "Обновлённая"));

        String body = """
                {
                "active": true,
                "icon": "icon3",
                "name": "Обновлённая",
                "description": "Новое",
                "version": 3
                }
                """;

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Обновлённая"))
                .andExpect(jsonPath("$.version").value(4));  // версия инкрементнулась

        verify(service).update(eq(1L), any());
    }

    @Test
    @DisplayName("PUT без version -> 400")
    void update_missingVersion_returns400() throws Exception {
        String body = """
                {"active": true, "icon": "icon1", "name": "X", "description": "Y"}
                """;

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.version").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PUT с отрицательной version -> 400")
    void update_negativeVersion_returns400() throws Exception {
        String body = """
                {"active": true, "icon": "icon1", "name": "X", "description": "Y", "version": -1}
                """;

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.version").exists());
    }

    @Test
    @DisplayName("PUT с устаревшей version -> 409 от ConflictException")
    void update_staleVersion_returns409() throws Exception {
        when(service.update(eq(1L), any()))
                .thenThrow(new ConflictException("Версия устарела: ожидалась 4"));

        String body = """
                {"active": true, "icon": "icon1", "name": "X", "description": "Y", "version": 2}
                """;

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())            // 409, не 500
                .andExpect(jsonPath("$.error").value("conflict"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PUT несуществующей записи -> 404 от NotFoundException")
    void update_notFound_returns404() throws Exception {
        when(service.update(eq(99L), any()))
                .thenThrow(new NotFoundException("Запись 99 не найдена"));

        String body = """
                {"active": true, "icon": "icon1", "name": "X", "description": "Y", "version": 0}
                """;

        mvc.perform(put("/api/records/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    // ======================= DELETE /api/records/{id}?version=N =======================

    @Test
    @DisplayName("DELETE с корректной version -> 204 без тела")
    void delete_returns204() throws Exception {
        doNothing().when(service).delete(1L, 3L);

        mvc.perform(delete("/api/records/1").param("version", "3"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));   // 204 = пустое тело

        verify(service).delete(1L, 3L);
    }

    @Test
    @DisplayName("DELETE без version -> 400 (missing request param)")
    void delete_missingVersion_returns400() throws Exception {
        mvc.perform(delete("/api/records/1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("DELETE с устаревшей version -> 409")
    void delete_staleVersion_returns409() throws Exception {
        doThrow(new ConflictException("Версия устарела: ожидалась 4"))
                .when(service).delete(1L, 2L);

        mvc.perform(delete("/api/records/1").param("version", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    @DisplayName("DELETE несуществующей записи -> 404")
    void delete_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("Запись 99 не найдена"))
                .when(service).delete(99L, 0L);

        mvc.perform(delete("/api/records/99").param("version", "0"))
                .andExpect(status().isNotFound());
    }

    // ==== Проверка GlobalExceptionHandler — не замапить 500 ====

    @Test
    @DisplayName("OptimisticLockingFailureException из сервиса -> 409, не 500")
    void update_optimisticLockFromFlush_returns409() throws Exception {
        // Имитируем остаточную гонку: сервис проверку прошёл,
        // но на flush Hibernate бросил OptimisticLockingFailureException.
        when(service.update(eq(1L), any()))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("stale"));

        String body = """
                {"active": true, "icon": "icon1", "name": "X", "description": "Y", "version": 3}
                """;

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }
}