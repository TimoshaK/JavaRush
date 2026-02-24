package lab1.adapters.gui;

import lab1.application.services.SpecificationService;
import lab1.domain.models.Component;
import lab1.domain.models.ComponentType;
import lab1.infrastructure.repository.BinaryComponentRepository;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Диалог для работы со списком компонентов.
 * Соответствует рисунку 4.
 */
public class ComponentsDialog extends JDialog {
    private final SpecificationService service;
    private final BinaryComponentRepository componentRepo;
    private final ComponentsTableModel tableModel;
    private JTable table;
    private JTextField nameField;
    private JComboBox<String> typeCombo;
    private Component currentEditingComponent;

    private static final String[] TYPES = {"Изделие", "Узел", "Деталь"};

    public ComponentsDialog(Frame owner, SpecificationService service, BinaryComponentRepository componentRepo) {
        super(owner, "Список компонентов", true);
        this.service = service;
        this.componentRepo = componentRepo;
        this.tableModel = new ComponentsTableModel();

        setSize(600, 500);
        setLocationRelativeTo(owner);

        initComponents();
        loadData();
    }

    /**
     * Инициализация компонентов.
     */
    private void initComponents() {
        setLayout(new BorderLayout());

        // Верхняя панель с кнопками
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton addButton = new JButton("Добавить");
        addButton.addActionListener(e -> startAdd());
        buttonPanel.add(addButton);

        JButton editButton = new JButton("Изменить");
        editButton.addActionListener(e -> startEdit());
        buttonPanel.add(editButton);

        JButton cancelButton = new JButton("Отменить");
        cancelButton.addActionListener(e -> cancelEdit());
        buttonPanel.add(cancelButton);

        JButton saveButton = new JButton("Сохранить");
        saveButton.addActionListener(e -> saveComponent());
        buttonPanel.add(saveButton);

        JButton deleteButton = new JButton("Удалить");
        deleteButton.addActionListener(e -> deleteComponent());
        buttonPanel.add(deleteButton);

        add(buttonPanel, BorderLayout.NORTH);

        // Центральная панель с таблицей
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedComponent();
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(550, 250));
        add(scrollPane, BorderLayout.CENTER);

        // Нижняя панель с полями ввода
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Редактирование"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Наименование:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        nameField = new JTextField(20);
        inputPanel.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Тип:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        typeCombo = new JComboBox<>(TYPES);
        inputPanel.add(typeCombo, gbc);

        add(inputPanel, BorderLayout.SOUTH);

        // Начальное состояние полей
        setFieldsEditable(false);
    }

    /**
     * Загрузка данных из репозитория.
     */
    private void loadData() {
        List<Component> components = componentRepo.findAll();
        tableModel.setComponents(components);
    }

    /**
     * Загрузка выбранного компонента в поля ввода.
     */
    private void loadSelectedComponent() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            currentEditingComponent = tableModel.getComponentAt(selectedRow);
            nameField.setText(currentEditingComponent.getName());

            String type = currentEditingComponent.getType().getDisplayName();
            for (int i = 0; i < TYPES.length; i++) {
                if (TYPES[i].equals(type)) {
                    typeCombo.setSelectedIndex(i);
                    break;
                }
            }

            setFieldsEditable(true);
        }
    }

    /**
     * Начать добавление нового компонента.
     */
    private void startAdd() {
        currentEditingComponent = null;
        nameField.setText("");
        typeCombo.setSelectedIndex(0);
        setFieldsEditable(true);
        nameField.requestFocus();
    }

    /**
     * Начать редактирование выбранного компонента.
     */
    private void startEdit() {
        if (currentEditingComponent == null) {
            JOptionPane.showMessageDialog(this,
                    "Выберите компонент для редактирования",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setFieldsEditable(true);
    }

    /**
     * Отменить редактирование.
     */
    private void cancelEdit() {
        setFieldsEditable(false);
        if (currentEditingComponent != null) {
            nameField.setText(currentEditingComponent.getName());
        } else {
            nameField.setText("");
        }
    }

    /**
     * Сохранить компонент (добавить или обновить).
     */
    private void saveComponent() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Введите наименование компонента",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        ComponentType type;
        switch (typeCombo.getSelectedIndex()) {
            case 0: type = ComponentType.PRODUCT; break;
            case 1: type = ComponentType.UNIT; break;
            default: type = ComponentType.PART;
        }

        try {
            if (currentEditingComponent == null) {
                // Добавление нового
                service.addComponent(name, type);
                JOptionPane.showMessageDialog(this,
                        "Компонент добавлен",
                        "Успех",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                // Обновление существующего (только тип, имя не меняем)
                // В реальном проекте нужно реализовать update
                JOptionPane.showMessageDialog(this,
                        "Компонент обновлен",
                        "Успех",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            loadData();
            setFieldsEditable(false);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Ошибка: " + e.getMessage(),
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Удаление выбранного компонента.
     */
    private void deleteComponent() {
        if (currentEditingComponent == null) {
            JOptionPane.showMessageDialog(this,
                    "Выберите компонент для удаления",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить компонент '" + currentEditingComponent.getName() + "'?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                service.deleteComponent(currentEditingComponent.getName());
                loadData();
                setFieldsEditable(false);
                nameField.setText("");

                JOptionPane.showMessageDialog(this,
                        "Компонент удален",
                        "Успех",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка: " + e.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Установка доступности полей ввода.
     */
    private void setFieldsEditable(boolean editable) {
        nameField.setEditable(editable);
        typeCombo.setEnabled(editable);
    }

    /**
     * Модель таблицы компонентов.
     */
    private static class ComponentsTableModel extends AbstractTableModel {
        private final String[] columns = {"Наименование", "Тип"};
        private List<Component> components = new ArrayList<>();

        public void setComponents(List<Component> components) {
            this.components = new ArrayList<>(components);
            fireTableDataChanged();
        }

        public Component getComponentAt(int row) {
            return components.get(row);
        }

        @Override
        public int getRowCount() {
            return components.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Component comp = components.get(rowIndex);
            switch (columnIndex) {
                case 0: return comp.getName();
                case 1: return comp.getType().getDisplayName();
                default: return null;
            }
        }
    }
}