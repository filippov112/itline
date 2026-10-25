// Только отображение и локальные обработчики.
// Никаких fetch — все действия делегируются в main.js/state.js.
// Так проще тестировать: UI-функции детерминированы по входному state.

const ICONS = ["icon1", "icon2", "icon3"];

/** Рендер таблицы по снимку состояния. */
export function renderTable(state, handlers) {
    const tbody = document.getElementById("records-body");
    const empty = document.getElementById("empty-state");
    const deleteBtn = document.getElementById("btn-delete");

    tbody.innerHTML = "";

    for (const rec of state.records) {
        const tr = document.createElement("tr");
        tr.dataset.id = String(rec.id);
        if (rec.id === state.selectedId) tr.classList.add("is-selected");

        // 1) radio для выбора строки под удаление.
        const tdSelect = document.createElement("td");
        tdSelect.className = "records__select";
        const radio = document.createElement("input");
        radio.type = "radio";
        radio.name = "row-select";
        radio.checked = rec.id === state.selectedId;
        radio.addEventListener("change", () => handlers.onSelect(rec.id));
        tdSelect.appendChild(radio);

        // 2) чекбокс "активно" — это данные строки, шлём PATCH/PUT.
        const tdActive = document.createElement("td");
        tdActive.className = "records__active";
        const active = document.createElement("input");
        active.type = "checkbox";
        active.checked = !!rec.active;
        active.disabled = state.loading;
        active.addEventListener("change", () => handlers.onToggleActive(rec));
        tdActive.appendChild(active);

        // 3) иконка 32×32 из static/icons.
        const tdIcon = document.createElement("td");
        tdIcon.className = "records__icon";
        const img = document.createElement("img");
        img.src = `/icons/${safeIcon(rec.icon)}.svg`;
        img.alt = "Иконка";
        img.width = 32;
        img.height = 32;
        tdIcon.appendChild(img);

        // 4) название/описание — textContent, а не innerHTML (XSS).
        const tdName = document.createElement("td");
        tdName.className = "records__name";
        tdName.textContent = rec.name;

        const tdDesc = document.createElement("td");
        tdDesc.className = "records__desc";
        tdDesc.textContent = rec.description || "—";

        tr.append(tdSelect, tdActive, tdIcon, tdName, tdDesc);
        tbody.appendChild(tr);
    }

    empty.hidden = state.records.length > 0;
    // Кнопка "Удалить" активна только при выбранной строке и не во время загрузки.
    deleteBtn.disabled = state.selectedId == null || state.loading;

    renderGlobalError(state.lastError);
}

function safeIcon(icon) {
    // Защита от подстановки произвольного пути из БД.
    return ICONS.includes(icon) ? icon : ICONS[0];
}

function renderGlobalError(message) {
    const el = document.getElementById("global-error");
    if (!message) {
        el.hidden = true;
        el.textContent = "";
        return;
    }
    el.textContent = message;
    el.hidden = false;
}

// ---------- Модалка "Добавить" ----------

export function openAddDialog() {
    const dialog = document.getElementById("add-dialog");
    document.getElementById("add-form").reset();
    document.getElementById("add-active").checked = true;
    document.getElementById("add-icon").value = "icon1";
    hideInlineError("add-error");
    dialog.showModal();
}

export function closeAddDialog() {
    document.getElementById("add-dialog").close();
}

/** Читает форму. Валидация — в main.js, здесь только сбор данных. */
export function readAddForm() {
    return {
        active: document.getElementById("add-active").checked,
        icon: document.getElementById("add-icon").value,
        name: document.getElementById("add-name").value.trim(),
        description: document.getElementById("add-description").value.trim(),
    };
}

export function showInlineError(id, message) {
    const el = document.getElementById(id);
    el.textContent = message;
    el.hidden = false;
}

export function hideInlineError(id) {
    const el = document.getElementById(id);
    el.textContent = "";
    el.hidden = true;
}

// ---------- Модалка подтверждения удаления ----------

export function openDeleteDialog(record) {
    document.getElementById("delete-text").textContent =
        `Вы точно хотите удалить ${record.name}?`;
    hideInlineError("delete-error");
    document.getElementById("delete-dialog").showModal();
}

export function closeDeleteDialog() {
    document.getElementById("delete-dialog").close();
}