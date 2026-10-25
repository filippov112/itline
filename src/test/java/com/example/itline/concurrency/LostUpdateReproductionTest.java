package com.example.itline.concurrency;

import com.example.itline.domain.Record;
import com.example.itline.repo.RecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired RecordRepository repo;
    @Autowired TransactionTemplate tx;

    @Test
    void lostUpdate_withoutVersioning() throws Exception {
        Record initial = repo.save(new Record(false, "icon1", "Исходное", "desc"));
        Long id = initial.getId();

        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> tx.execute(status -> {
                Record r = repo.findById(id).orElseThrow();
                String old = r.getName();
                try {
                    bothRead.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                r.setName(old + " + " + Thread.currentThread().getName());
                return null;
            })));
        }

        for (Future<?> f : futures) {
            assertThatCode(f::get).doesNotThrowAnyException();
        }

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        Record result = repo.findById(id).orElseThrow();
        long plusCount = result.getName().chars().filter(c -> c == '+').count();
        assertThat(plusCount)
                .as("Без @Version одно обновление должно потеряться")
                .isEqualTo(1);
    }


    @Test
    void withVersioning_secondWriterConflicts() throws Exception {
        Record initial = repo.save(new Record(false, "icon1", "Исходное", "desc"));
        Long id = initial.getId();

        CyclicBarrier bothRead = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> tx.execute(status -> {
                Record r = repo.findById(id).orElseThrow();
                String old = r.getName();
                try { bothRead.await(5, TimeUnit.SECONDS); }
                catch (Exception e) { throw new RuntimeException(e); }
                r.setName(old + " + " + Thread.currentThread().getName());
                return null;
            })));
        }

        // Ровно один должен упасть с optimistic lock, второй — пройти
        int conflicts = 0, successes = 0;
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); successes++; }
            catch (ExecutionException e) {
                assertThat(e.getCause())
                    .isInstanceOf(OptimisticLockingFailureException.class);
                conflicts++;
            }
        }
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);

        // В БД — ровно одно обновление, второй не затёр
        Record result = repo.findById(id).orElseThrow();
        long plusCount = result.getName().chars().filter(c -> c == '+').count();
        assertThat(plusCount).isEqualTo(1);
        // И версия увеличилась ровно на 1 (0 -> 1), а не на 2
        assertThat(result.getVersion()).isEqualTo(1L);
    }
}