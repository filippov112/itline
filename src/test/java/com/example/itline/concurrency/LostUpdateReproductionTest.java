package com.example.itline.concurrency;

import com.example.itline.domain.Record;
import com.example.itline.repo.RecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class LostUpdateReproductionTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("itline").withUsername("itline").withPassword("itline");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        // Для теста пусть схема создаётся с нуля
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired RecordRepository repo;

    @Test
    void twoThreadsReadThenWrite_lostUpdate() throws Exception {
        Record initial = repo.save(new Record(false, "icon1", "Исходное", "desc"));
        Long id = initial.getId();

        // Барьер на 2: оба потока прочитали — оба могут идти писать
        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Runnable work = () -> {
            try {
                // --- Фаза 1: чтение ---
                var r = repo.findById(id).orElseThrow();
                String oldName = r.getName();       // запоминаем, что прочитали

                bothRead.await();                   // ждём, пока второй тоже прочитает

                // --- Фаза 2: запись ---
                // пишем на основе ТОГО ЖЕ объекта r, который прочитали.
                r.setName(oldName + " + " + Thread.currentThread().getName());
                repo.save(r);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        pool.submit(work);
        pool.submit(work);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        var result = repo.findById(id).orElseThrow();

        // Ключевая проверка: имя содержит только одно " + thread",
        // а не оба — значит, одна запись затёрла другую.
        long plusCount = result.getName().chars().filter(c -> c == '+').count();
        assertThat(plusCount)
            .as("Если оба обновления применились последовательно, было бы 2 '+'")
            .isEqualTo(1);   // ровно одно обновление потеряно
    }
}