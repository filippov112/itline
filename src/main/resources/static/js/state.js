// Централизует клиентское состояние и правила его обновления.
// Идея: UI никогда не правит массив записей напрямую — только через
// методы этого модуля. Так проще гарантировать, что после неуспешного
// запроса состояние соответствует БД.
//
// Простая подписка (без библиотек): setState -> listeners.

import * as api from "./api.js";

const listeners = new Set();

/** @typedef {{id:number, active:boolean, icon:string, name:string, description:string, version:number}} Record */

const state = {
    records: /** @type {Record[]} */ ([]),
    selectedId: /** @type {number|null} */ (null),
    loading: false,
    lastError: /** @type {string|null} */ (null),
};

export function subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
}

function emit() {
    for (const fn of listeners) fn(getState());
}

/** Возвращает снимок — UI не должен мутировать внутренний массив. */
export function getState() {
    return {
        records: state.records.slice(),
        selectedId: state.selectedId,
        loading: state.loading,
        lastError: state.lastError,
    };
}

/**
 * Полное перечитывание с сервера.
 * Используется:
 *  - при старте;
 *  - после 409/404 — чтобы гарантированно синхронизироваться с БД;
 *  - после любого "непонятного" сбоя.
 */
export async function reload() {
    state.loading = true;
    state.lastError = null;
    emit();
    try {
        state.records = await api.listRecords();
        // если выбранной записи больше нет — снимаем выбор,
        // иначе кнопка "Удалить" осталась бы активной для несуществующей строки
        if (state.selectedId != null && !state.records.some(r => r.id === state.selectedId)) {
            state.selectedId = null;
        }
    } catch (e) {
        state.lastError = humanize(e);
    } finally {
        state.loading = false;
        emit();
    }
}

export function select(id) {
    state.selectedId = state.selectedId === id ? null : id;
    emit();
}

/**
 * Создание. Возвращает true при успехе, чтобы UI закрыл модалку.
 * При ошибке состояние не трогаем: список на экране остаётся тем,
 * что подтверждён сервером.
 */
export async function create(form) {
    try {
        const created = await api.createRecord(form);
        state.records = [...state.records, created];
        state.lastError = null;
        emit();
        return true;
    } catch (e) {
        if (e.kind === "conflict" || e.kind === "network" || e.kind === "server") {
            // На всякий случай перечитываем: возможно, запись уже создана,
            // просто ответ потерялся по дороге.
            await reload();
        }
        state.lastError = humanize(e);
        emit();
        return false;
    }
}

/**
 * Переключение чекбокса "активно".
 * Ждём ответ сервера и только тогда меняем строку. Так UI не разойдётся с БД при 409.
 * При конфликте — перечитываем и показываем сообщение.
 */
export async function toggleActive(record) {
    try {
        const updated = await api.updateRecord(
            record.id,
            {
                active: !record.active,
                icon: record.icon,
                name: record.name,
                description: record.description,
            },
            record.version,
        );
        replaceRecord(updated);
        state.lastError = null;
    } catch (e) {
        // Любая ошибка -> состояние должно соответствовать БД.
        // reload() дешёвый (список небольшой), зато гарантирует консистентность.
        await reload();
        state.lastError = humanize(e);
    }
    emit();
}

/**
 * Удаление выбранной записи.
 */
export async function removeSelected() {
    const rec = state.records.find(r => r.id === state.selectedId);
    if (!rec) return { ok: false, error: null };

    try {
        await api.deleteRecord(rec.id, rec.version);
        state.records = state.records.filter(r => r.id !== rec.id);
        state.selectedId = null;
        state.lastError = null;
        emit();
        return { ok: true, error: null };
    } catch (e) {
        await reload();
        state.lastError = humanize(e);
        emit();
        return { ok: false, error: humanize(e) };
    }
}

function replaceRecord(updated) {
    state.records = state.records.map(r => (r.id === updated.id ? updated : r));
}

/** Превращает ApiError в текст для UI. */
export function humanize(e) {
    if (!e) return null;
    switch (e.kind) {
        case "conflict":   return "Запись изменена или удалена в другой сессии. Данные обновлены.";
        case "validation": return "Некорректные данные. Проверьте поля.";
        case "network":    return "Нет связи с сервером. Повторите попытку.";
        default:           return e.message || "Неизвестная ошибка.";
    }
}