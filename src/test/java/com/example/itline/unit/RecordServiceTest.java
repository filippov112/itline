package com.example.itline.unit;

import com.example.itline.domain.Record;
import com.example.itline.repo.RecordRepository;
import com.example.itline.service.ConflictException;
import com.example.itline.service.NotFoundException;
import com.example.itline.service.RecordService;
import com.example.itline.web.dto.CreateRecordRequest;
import com.example.itline.web.dto.UpdateRecordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты RecordService.
 *
 * - RecordRepository — мок: имитируем ответы «БД».
 * - Проверяем логику сервиса: ветвления (404/409), маппинг полей,
 *   вызовы репозитория. Реальный @Version проверяется в integration-тестах.
 */
@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    @Mock
    private RecordRepository repo;

    @InjectMocks
    private RecordService service;

    private Record existing;

    @BeforeEach
    void setUp() {
        // Свежая сущность для каждого теста — никакого шаринга состояния.
        existing = new Record(false, "icon1", "Старое имя", "Старое описание");
        TestRecords.setId(existing, 1L);
        TestRecords.setVersion(existing, 3L);
    }

    // ======================= findAll =======================

    @Test
    @DisplayName("findAll возвращает то, что вернул репозиторий")
    void findAll_returnsRepositoryResult() {
        when(repo.findAll()).thenReturn(List.of(existing));

        List<Record> result = service.findAll();

        assertThat(result).containsExactly(existing);
        verify(repo).findAll();
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("findAll на пустой БД возвращает пустой список")
    void findAll_empty() {
        when(repo.findAll()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    // ======================= create =======================

    @Test
    @DisplayName("create маппит поля из DTO и вызывает save")
    void create_mapsFieldsAndSaves() {
        CreateRecordRequest req = new CreateRecordRequest(
                true, "icon2", "Новое имя", "Новое описание");

        // save возвращает аргумент, но с «назначенным» id.
        // Имитируем поведение JPA: после save id != null.
        when(repo.save(any(Record.class))).thenAnswer(inv -> {
            Record r = inv.getArgument(0);
            TestRecords.setId(r, 42L);
            return r;
        });

        Record created = service.create(req);

        assertThat(created.getId()).isEqualTo(42L);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getIcon()).isEqualTo("icon2");
        assertThat(created.getName()).isEqualTo("Новое имя");
        assertThat(created.getDescription()).isEqualTo("Новое описание");
        verify(repo).save(any(Record.class));
    }

    // ======================= update =======================

    @Test
    @DisplayName("update: нет записи -> NotFoundException")
    void update_notFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        UpdateRecordRequest req = new UpdateRecordRequest(
                true, "icon1", "Имя", "Описание", 3L);

        assertThatThrownBy(() -> service.update(99L, req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("update: устаревшая version -> ConflictException, save не вызывается")
    void update_staleVersion() {
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        // existing.version = 3, а клиент прислал 2
        UpdateRecordRequest req = new UpdateRecordRequest(
                true, "icon1", "Новое имя", "Новое описание", 2L);

        assertThatThrownBy(() -> service.update(1L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3");

        // поля существующей сущности не изменились.
        assertThat(existing.getName()).isEqualTo("Старое имя");
    }

    @Test
    @DisplayName("update: корректная version -> поля обновлены, save вызван")
    void update_success() {
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        UpdateRecordRequest req = new UpdateRecordRequest(
                true, "icon3", "Новое имя", "Новое описание", 3L);

        Record updated = service.update(1L, req);

        assertThat(updated).isSameAs(existing);
        assertThat(updated.isActive()).isTrue();
        assertThat(updated.getIcon()).isEqualTo("icon3");
        assertThat(updated.getName()).isEqualTo("Новое имя");
        assertThat(updated.getDescription()).isEqualTo("Новое описание");
        // version НЕ трогаем руками — этим занимается Hibernate на flush.
        // Если бы мы изменили version в коде, тест бы это поймал (но у нас нет сеттера).
    }

    @Test
    @DisplayName("update не меняет version вручную — это работа Hibernate")
    void update_doesNotTouchVersion() {
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        long before = existing.getVersion();
        service.update(1L, new UpdateRecordRequest(true, "icon1", "X", "Y", 3L));

        assertThat(existing.getVersion()).isEqualTo(before);
    }

    // ======================= delete =======================

    @Test
    @DisplayName("delete: нет записи -> NotFoundException")
    void delete_notFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 3L))
                .isInstanceOf(NotFoundException.class);

        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("delete: устаревшая version -> ConflictException, delete не вызывается")
    void delete_staleVersion() {
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        // existing.version = 3, клиент прислал 2
        assertThatThrownBy(() -> service.delete(1L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3");

        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("delete: корректная version -> delete вызван с нужной сущностью")
    void delete_success() {
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L, 3L);

        verify(repo).delete(existing);
    }

}