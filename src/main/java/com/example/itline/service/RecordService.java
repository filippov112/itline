package com.example.itline.service;

import com.example.itline.domain.Record;
import com.example.itline.repo.RecordRepository;
import com.example.itline.web.dto.CreateRecordRequest;
import com.example.itline.web.dto.UpdateRecordRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Вся бизнес-логика
 */
@Service
public class RecordService {

    private final RecordRepository repo;

    public RecordService(RecordRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Record> findAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Record findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Запись " + id + " не найдена"));
    }

    @Transactional
    public Record create(CreateRecordRequest req) {
        Record r = new Record(req.active(), req.icon(), req.name(), req.description());
        return repo.save(r);
    }

    @Transactional
    public Record update(Long id, UpdateRecordRequest req) {
        Record r = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Запись " + id + " не найдена"));

        // Быстрая проверка: клиент прислал устаревшую версию.
        if (r.getVersion() != req.version()) {
            throw new ConflictException("Версия устарела: ожидалась " + r.getVersion());
        }
        r.setActive(req.active());
        r.setIcon(req.icon());
        r.setName(req.name());
        r.setDescription(req.description());
        // save() НЕ нужен: dirty checking сделает UPDATE при коммите.
        // при реальной гонке Hibernate бросит OptimisticLockingFailureException, его перехватит advice.
        return r;
    }

    @Transactional
    public void delete(Long id, Long version) {
        Record r = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Запись " + id + " не найдена"));
        if (r.getVersion() != version) {
            throw new ConflictException("Версия устарела: ожидалась " + r.getVersion());
        }
        // Для DELETE optimistic locking тоже работает: Hibernate добавит
        // version в WHERE. Если строк 0 — исключение -> 409.
        repo.delete(r);
    }
}