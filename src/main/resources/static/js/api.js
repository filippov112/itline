// Задача модуля: единая точка общения с сервером.
// Здесь же — нормализация ошибок: UI не должен знать про HTTP-статусы,
// он получает либо данные, либо ApiError с полем kind.
//
// Почему так: требование "любой неуспешный запрос оставляет интерфейс
// в состоянии, соответствующем БД" проще выполнить, если ошибки
// классифицированы в одном месте, а не размазаны по обработчикам.

const BASE = "/api/records";

/** Ошибка запроса с типом: 'conflict' | 'validation' | 'network' | 'server'. */
export class ApiError extends Error {
    constructor(kind, message, status = 0, payload = null) {
        super(message);
        this.name = "ApiError";
        this.kind = kind;
        this.status = status;
        this.payload = payload;
    }
}

/**
 * Обёртка над fetch:
 * - JSON-заголовки;
 * - таймаут через AbortController (медленная сеть не должна "вешать" UI);
 * - разбор тела и классификация ошибок.
 */
async function request(path, { method = "GET", body, timeoutMs = 15000 } = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    let res;
    try {
        res = await fetch(path, {
            method,
            headers: body ? { "Content-Type": "application/json" } : undefined,
            body: body ? JSON.stringify(body) : undefined,
            signal: controller.signal,
            // важно для корректной работы с cookies/CSRF, если появятся
            credentials: "same-origin",
        });
    } catch (e) {
        clearTimeout(timer);
        // AbortError или сетевая ошибка — для UI это "retryable"
        throw new ApiError("network", "Нет связи с сервером", 0, null);
    }
    clearTimeout(timer);

    // 204 No Content — тело читать нельзя
    if (res.status === 204) return null;

    const text = await res.text();
    const payload = text ? safeJson(text) : null;

    if (res.ok) return payload;

    if (res.status === 409) {
        throw new ApiError("conflict", "Запись уже изменена в другой сессии", 409, payload);
    }
    if (res.status === 400 || res.status === 422) {
        throw new ApiError("validation", "Проверьте поля", res.status, payload);
    }
    if (res.status === 404) {
        throw new ApiError("conflict", "Запись уже удалена", 404, payload);
    }
    throw new ApiError("server", "Ошибка сервера", res.status, payload);
}

function safeJson(text) {
    try { return JSON.parse(text); } catch { return { raw: text }; }
}

// ---- Публичный контракт, которым пользуется state.js ----

export async function listRecords() {
    const data = await request(BASE);
    return Array.isArray(data) ? data : [];
}

export async function createRecord(payload) {
    // payload: { active: boolean, icon: 'icon1'|'icon2'|'icon3', name, description }
    return request(BASE, { method: "POST", body: payload });
}

/**
 * Обновление
 */
export async function updateRecord(id, payload, version) {
    return request(`${BASE}/${id}`, {
        method: "PUT",
        body: { ...payload, version },
    });
}

/**
 * Удаление
 */
export async function deleteRecord(id, version) {
    return request(`${BASE}/${id}?version=${encodeURIComponent(version)}`, {
        method: "DELETE",
    });
}