// Точка входа. Связывает state <-> ui и обрабатывает пользовательские
// сценарии. Здесь же — защита от "быстрых кликов": пока запрос
// в полёте, повторные действия игнорируются.

import * as state from "./state.js";
import * as ui from "./ui.js";

// Флаг "идёт запрос" — защита от двойных сабмитов.
let inFlight = false;

async function withLock(fn) {
    if (inFlight) return;
    inFlight = true;
    try { await fn(); } finally { inFlight = false; }
}

// ---------- Инициализация ----------

const handlers = {
    onSelect: (id) => state.select(id),
    onToggleActive: (rec) => withLock(() => state.toggleActive(rec)),
};

state.subscribe((s) => ui.renderTable(s, handlers));

// Первичная загрузка + периодическое обновление не нужно:
// после каждой мутации мы сами приводим состояние в порядок.

// ---------- "Добавить" ----------

document.getElementById("btn-add").addEventListener("click", () => {
    ui.openAddDialog();
});

document.getElementById("add-cancel").addEventListener("click", () => {
    ui.closeAddDialog();
});

document.getElementById("add-form").addEventListener("submit", (e) => {
    // Перехватываем submit, чтобы страница не перезагружалась
    e.preventDefault();
    withLock(async () => {
        const form = ui.readAddForm();

        // Валидация до сети: пустые поля -> окно не закрываем,
        // показываем сообщение, запрос не уходит.
        if (!form.name) {
            ui.showInlineError("add-error", "Укажите название.");
            return;
        }
        if (!form.description) {
            ui.showInlineError("add-error", "Укажите описание.");
            return;
        }
        ui.hideInlineError("add-error");

        // Блокируем кнопку на время запроса — иначе двойной сабмит
        // создаст две записи при медленной сети.
        const submit = document.getElementById("add-submit");
        submit.disabled = true;
        try {
            const ok = await state.create(form);
            if (ok) {
                ui.closeAddDialog();
            } else {
                // state уже перечитал список; показываем понятное сообщение
                ui.showInlineError(
                    "add-error",
                    "Не удалось сохранить. Список обновлён, попробуйте снова.",
                );
            }
        } finally {
            submit.disabled = false;
        }
    });
});

// ---------- "Удалить" ----------

document.getElementById("btn-delete").addEventListener("click", () => {
    const s = state.getState();
    const rec = s.records.find(r => r.id === s.selectedId);
    if (!rec) return;
    ui.openDeleteDialog(rec);
});

document.getElementById("delete-cancel").addEventListener("click", () => {
    ui.closeDeleteDialog();
});

document.getElementById("delete-confirm").addEventListener("click", () => {
    withLock(async () => {
        const confirmBtn = document.getElementById("delete-confirm");
        confirmBtn.disabled = true;
        try {
            const { ok, error } = await state.removeSelected();
            if (ok) {
                ui.closeDeleteDialog();
            } else {
                // Оставляем диалог открытым и показываем причину;
                // состояние уже перечитано с сервера.
                ui.showInlineError(
                    "delete-error",
                    error || "Не удалось удалить запись.",
                );
            }
        } finally {
            confirmBtn.disabled = false;
        }
    });
});

// ---------- Старт ----------

state.reload();