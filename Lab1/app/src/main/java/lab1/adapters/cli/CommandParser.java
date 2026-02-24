package lab1.adapters.cli;

import lab1.domain.models.ComponentType;
import lab1.domain.exceptions.DomainException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер команд для консольного интерфейса.
 * Разбирает введенную строку на команду и параметры.
 */
public class CommandParser {
    // Упрощенные регулярные выражения
    private static final Pattern CREATE_PATTERN =
            Pattern.compile("^Create\\s+(.+?)(?:,\\s*(\\d+))?(?:,\\s*(.+))?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern OPEN_PATTERN =
            Pattern.compile("^Open\\s+(.+?)(?:,\\s*(.+))?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_COMPONENT_PATTERN =
            Pattern.compile("^Input\\s+\\((.+?),(.+?)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_SPEC_PATTERN =
            Pattern.compile("^Input\\s+\\((.+?)/(.+?)(?:,(\\d+))?\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DELETE_COMPONENT_PATTERN =
            Pattern.compile("^Delete\\s+\\((.+?)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DELETE_SPEC_PATTERN =
            Pattern.compile("^Delete\\s+\\((.+?)/(.+?)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern RESTORE_COMPONENT_PATTERN =
            Pattern.compile("^Restore\\s+\\((.+?)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern RESTORE_ALL_PATTERN =
            Pattern.compile("^Restore\\s+\\(\\*\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRINT_COMPONENT_PATTERN =
            Pattern.compile("^Print\\s+\\((.+?)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRINT_ALL_PATTERN =
            Pattern.compile("^Print\\s+\\(\\*\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern HELP_PATTERN =
            Pattern.compile("^Help(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern EXIT_PATTERN =
            Pattern.compile("^Exit$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRUNCATE_PATTERN =
            Pattern.compile("^Truncate$", Pattern.CASE_INSENSITIVE);

    /**
     * Типы команд.
     */
    public enum CommandType {
        CREATE,
        OPEN,
        INPUT_COMPONENT,
        INPUT_SPEC,
        DELETE_COMPONENT,
        DELETE_SPEC,
        RESTORE_COMPONENT,
        RESTORE_ALL,
        PRINT_COMPONENT,
        PRINT_ALL,
        TRUNCATE,
        HELP,
        EXIT,
        UNKNOWN
    }

    /**
     * Результат парсинга команды.
     */
    public static class ParseResult {
        private final CommandType type;
        private final List<String> parameters;
        private final Map<String, Object> namedParams;

        public ParseResult(CommandType type) {
            this.type = type;
            this.parameters = new ArrayList<>();
            this.namedParams = new HashMap<>();
        }

        public ParseResult(CommandType type, String... params) {
            this.type = type;
            this.parameters = new ArrayList<>();
            this.namedParams = new HashMap<>();
            if (params != null) {
                for (String param : params) {
                    if (param != null) {
                        this.parameters.add(param.trim());
                    }
                }
            }
        }

        public CommandType getType() {
            return type;
        }

        public List<String> getParameters() {
            return Collections.unmodifiableList(parameters);
        }

        public String getParameter(int index) {
            return index < parameters.size() ? parameters.get(index) : null;
        }

        public void addNamedParam(String key, Object value) {
            namedParams.put(key, value);
        }

        public Object getNamedParam(String key) {
            return namedParams.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T getNamedParam(String key, Class<T> type) {
            Object value = namedParams.get(key);
            return type.isInstance(value) ? (T) value : null;
        }
    }

    /**
     * Парсит введенную команду.
     *
     * @param input строка ввода
     * @return результат парсинга
     */
    public ParseResult parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ParseResult(CommandType.UNKNOWN);
        }

        String trimmed = input.trim();

        // Сначала проверяем простые команды без параметров
        if (EXIT_PATTERN.matcher(trimmed).matches()) {
            return new ParseResult(CommandType.EXIT);
        }

        if (TRUNCATE_PATTERN.matcher(trimmed).matches()) {
            return new ParseResult(CommandType.TRUNCATE);
        }

        if (RESTORE_ALL_PATTERN.matcher(trimmed).matches()) {
            return new ParseResult(CommandType.RESTORE_ALL);
        }

        if (PRINT_ALL_PATTERN.matcher(trimmed).matches()) {
            return new ParseResult(CommandType.PRINT_ALL);
        }

        // Create
        Matcher m = CREATE_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String prdFile = m.group(1) != null ? m.group(1).trim() : "";
            String nameLengthStr = m.group(2);
            String prsFile = m.group(3);

            // Убираем расширение .prd если есть
            if (prdFile.endsWith(".prd")) {
                prdFile = prdFile.substring(0, prdFile.length() - 4);
            }

            int nameLength = 20; // по умолчанию
            if (nameLengthStr != null && !nameLengthStr.isEmpty()) {
                try {
                    nameLength = Integer.parseInt(nameLengthStr.trim());
                } catch (NumberFormatException e) {
                    // оставляем 20
                }
            }

            if (prsFile == null || prsFile.trim().isEmpty()) {
                prsFile = prdFile + ".prs";
            } else {
                prsFile = prsFile.trim();
                if (!prsFile.endsWith(".prs")) {
                    prsFile += ".prs";
                }
            }

            prdFile = prdFile + ".prd";

            ParseResult result = new ParseResult(CommandType.CREATE, prdFile, String.valueOf(nameLength), prsFile);
            result.addNamedParam("nameLength", nameLength);
            return result;
        }

        // Open
        m = OPEN_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String prdFile = m.group(1) != null ? m.group(1).trim() : "";
            String prsFile = m.group(2);

            if (!prdFile.endsWith(".prd")) {
                prdFile += ".prd";
            }

            if (prsFile == null || prsFile.trim().isEmpty()) {
                String baseName = prdFile.endsWith(".prd") ?
                        prdFile.substring(0, prdFile.length() - 4) : prdFile;
                prsFile = baseName + ".prs";
            } else {
                prsFile = prsFile.trim();
                if (!prsFile.endsWith(".prs")) {
                    prsFile += ".prs";
                }
            }

            return new ParseResult(CommandType.OPEN, prdFile, prsFile);
        }

        // Input компонента
        m = INPUT_COMPONENT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1) != null ? m.group(1).trim() : "";
            String typeStr = m.group(2) != null ? m.group(2).trim() : "Деталь";

            ComponentType type;
            switch (typeStr.toLowerCase()) {
                case "изделие":
                    type = ComponentType.PRODUCT;
                    break;
                case "узел":
                    type = ComponentType.UNIT;
                    break;
                default:
                    type = ComponentType.PART;
            }

            ParseResult result = new ParseResult(CommandType.INPUT_COMPONENT, name, typeStr);
            result.addNamedParam("type", type);
            return result;
        }

        // Input спецификации
        m = INPUT_SPEC_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String owner = m.group(1) != null ? m.group(1).trim() : "";
            String part = m.group(2) != null ? m.group(2).trim() : "";
            String quantityStr = m.group(3);

            int quantity = 1;
            if (quantityStr != null && !quantityStr.isEmpty()) {
                try {
                    quantity = Integer.parseInt(quantityStr.trim());
                } catch (NumberFormatException e) {
                    // оставляем 1
                }
            }

            ParseResult result = new ParseResult(CommandType.INPUT_SPEC, owner, part, String.valueOf(quantity));
            result.addNamedParam("quantity", quantity);
            return result;
        }

        // Delete компонента
        m = DELETE_COMPONENT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1) != null ? m.group(1).trim() : "";
            return new ParseResult(CommandType.DELETE_COMPONENT, name);
        }

        // Delete спецификации
        m = DELETE_SPEC_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String owner = m.group(1) != null ? m.group(1).trim() : "";
            String part = m.group(2) != null ? m.group(2).trim() : "";
            return new ParseResult(CommandType.DELETE_SPEC, owner, part);
        }

        // Restore компонента
        m = RESTORE_COMPONENT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1) != null ? m.group(1).trim() : "";
            return new ParseResult(CommandType.RESTORE_COMPONENT, name);
        }

        // Print компонента
        m = PRINT_COMPONENT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String name = m.group(1) != null ? m.group(1).trim() : "";
            return new ParseResult(CommandType.PRINT_COMPONENT, name);
        }

        // Help
        m = HELP_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String filename = m.group(1);
            if (filename != null && !filename.trim().isEmpty()) {
                return new ParseResult(CommandType.HELP, filename.trim());
            } else {
                return new ParseResult(CommandType.HELP);
            }
        }

        return new ParseResult(CommandType.UNKNOWN);
    }

    /**
     * Возвращает строку с описанием всех команд.
     *
     * @return справка по командам
     */
    public static String getHelp() {
        return """
                Доступные команды:
                
                Create имя_файла[, максимальная_длина_имени][, имя_файла.prs]
                  - Создать новые файлы
                  Пример: Create data, 30, data
                  Пример: Create data.prd, 30, data.prs
                
                Open имя_файла[, имя_файла.prs]
                  - Открыть существующие файлы
                  Пример: Open data
                  Пример: Open data.prd, data.prs
                
                Input (имя, тип)
                  - Добавить компонент (тип: Изделие, Узел, Деталь)
                  Пример: Input (Стол, Изделие)
                
                Input (владелец/комплектующее[, количество])
                  - Добавить в спецификацию
                  Пример: Input (Стол/Ножка, 4)
                
                Delete (имя)
                  - Удалить компонент
                  Пример: Delete (Стол)
                
                Delete (владелец/комплектующее)
                  - Удалить из спецификации
                  Пример: Delete (Стол/Ножка)
                
                Restore (имя)
                  - Восстановить компонент
                  Пример: Restore (Стол)
                
                Restore (*)
                  - Восстановить все удаленные записи
                
                Print (имя)
                  - Вывести спецификацию компонента
                  Пример: Print (Стол)
                
                Print (*)
                  - Вывести список всех компонентов
                
                Truncate
                  - Физически удалить помеченные записи
                
                Help [имя_файла]
                  - Вывести эту справку (в файл, если указано)
                
                Exit
                  - Выйти из программы
                """;
    }
}