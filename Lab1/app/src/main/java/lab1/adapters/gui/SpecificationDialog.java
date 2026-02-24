package lab1.adapters.gui;

import lab1.application.services.SpecificationService;
import lab1.domain.models.Component;
import lab1.domain.models.ComponentType;
import lab1.domain.models.SpecLine;
import lab1.infrastructure.repository.BinaryComponentRepository;
import lab1.infrastructure.repository.BinarySpecRepository;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.List;

/**
 * Диалог для просмотра и редактирования спецификации.
 * Соответствует рисунку 5.
 */
public class SpecificationDialog extends JDialog {
    private final SpecificationService service;
    private final BinaryComponentRepository componentRepo;
    private final BinarySpecRepository specRepo;

    private JTree tree;
    private DefaultTreeModel treeModel;
    private JTextField searchField;
    private JLabel selectedComponentLabel;

    private Component currentComponent;

    public SpecificationDialog(Frame owner,
                               SpecificationService service,
                               BinaryComponentRepository componentRepo,
                               BinarySpecRepository specRepo) {
        super(owner, "Спецификация", true);
        this.service = service;
        this.componentRepo = componentRepo;
        this.specRepo = specRepo;

        setSize(500, 600);
        setLocationRelativeTo(owner);

        initComponents();
        showComponentSelection();
    }

    /**
     * Инициализация компонентов.
     */
    private void initComponents() {
        setLayout(new BorderLayout());

        // Верхняя панель с поиском
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel searchLabel = new JLabel("Найти:");
        searchField = new JTextField();
        searchField.addActionListener(e -> searchComponent());

        JButton searchButton = new JButton("Поиск");
        searchButton.addActionListener(e -> searchComponent());

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        topPanel.add(searchPanel, BorderLayout.NORTH);

        // Информация о выбранном компоненте
        selectedComponentLabel = new JLabel("Выберите компонент");
        selectedComponentLabel.setFont(new Font("Arial", Font.BOLD, 14));
        selectedComponentLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        topPanel.add(selectedComponentLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // Центральная панель с деревом спецификации
        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Спецификация"));
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);

        // Нижняя панель с кнопками
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton addButton = new JButton("Добавить");
        addButton.addActionListener(e -> addSpecItem());
        buttonPanel.add(addButton);

        JButton editButton = new JButton("Изменить");
        editButton.addActionListener(e -> editSpecItem());
        buttonPanel.add(editButton);

        JButton deleteButton = new JButton("Удалить");
        deleteButton.addActionListener(e -> deleteSpecItem());
        buttonPanel.add(deleteButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Показ диалога выбора компонента.
     */
    private void showComponentSelection() {
        List<Component> components = componentRepo.findAllActive().stream()
                .filter(c -> c.getType().canHaveSpecification())
                .toList();

        if (components.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Нет компонентов, которые могут иметь спецификацию",
                    "Информация",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] componentNames = components.stream()
                .map(Component::getName)
                .toArray(String[]::new);

        String selected = (String) JOptionPane.showInputDialog(this,
                "Выберите компонент для просмотра спецификации:",
                "Выбор компонента",
                JOptionPane.QUESTION_MESSAGE,
                null,
                componentNames,
                componentNames[0]);

        if (selected != null) {
            componentRepo.findByName(selected).ifPresent(comp -> {
                currentComponent = comp;
                selectedComponentLabel.setText(comp.getName() + " (" + comp.getType().getDisplayName() + ")");
                loadSpecification();
            });
        }
    }

    /**
     * Загрузка спецификации в дерево.
     */
    private void loadSpecification() {
        if (currentComponent == null) return;

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(currentComponent.getName());
        buildTree(root, currentComponent);

        treeModel.setRoot(root);
        expandAllNodes();
    }

    /**
     * Рекурсивное построение дерева спецификации.
     */
    private void buildTree(DefaultMutableTreeNode node, Component owner) {
        List<SpecLine> specs = specRepo.findByOwnerActive(owner);

        for (SpecLine line : specs) {
            Component comp = line.getComponent();
            String nodeText = comp.getName();
            if (comp.getType() == ComponentType.PART) {
                nodeText += " [x" + line.getQuantity() + "]";
            }

            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(nodeText);
            node.add(childNode);

            if (comp.getType().canHaveSpecification()) {
                buildTree(childNode, comp);
            }
        }
    }

    /**
     * Разворачивание всех узлов дерева.
     */
    private void expandAllNodes() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    /**
     * Поиск компонента по имени.
     */
    private void searchComponent() {
        String searchText = searchField.getText().trim().toLowerCase();
        if (searchText.isEmpty()) {
            loadSpecification();
            return;
        }

        // Простой поиск - подсвечиваем узлы, содержащие текст
        // В реальном проекте можно реализовать более сложный поиск
        expandAllNodes();
        // TODO: реализовать подсветку найденных узлов
    }

    /**
     * Добавление элемента в спецификацию.
     */
    private void addSpecItem() {
        if (currentComponent == null) return;

        // Получаем список доступных компонентов
        List<Component> availableComponents = componentRepo.findAllActive().stream()
                .filter(c -> !c.equals(currentComponent))
                .toList();

        if (availableComponents.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Нет доступных компонентов для добавления",
                    "Информация",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] partNames = availableComponents.stream()
                .map(Component::getName)
                .toArray(String[]::new);

        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));

        JComboBox<String> partCombo = new JComboBox<>(partNames);
        JTextField quantityField = new JTextField("1");

        panel.add(new JLabel("Комплектующее:"));
        panel.add(partCombo);
        panel.add(new JLabel("Количество:"));
        panel.add(quantityField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Добавить в спецификацию", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String partName = (String) partCombo.getSelectedItem();
            int quantity;

            try {
                quantity = Integer.parseInt(quantityField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Некорректное количество",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (quantity <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Количество должно быть положительным",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                service.addSpecItem(currentComponent.getName(), partName, quantity);
                loadSpecification();

                JOptionPane.showMessageDialog(this,
                        "Элемент добавлен",
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
     * Редактирование выбранного элемента.
     */
    private void editSpecItem() {
        TreePath selectedPath = tree.getSelectionPath();
        if (selectedPath == null) {
            JOptionPane.showMessageDialog(this,
                    "Выберите элемент для редактирования",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // TODO: реализовать редактирование количества
        JOptionPane.showMessageDialog(this,
                "Редактирование доступно только в платной версии",
                "Информация",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Удаление выбранного элемента.
     */
    private void deleteSpecItem() {
        TreePath selectedPath = tree.getSelectionPath();
        if (selectedPath == null || currentComponent == null) {
            JOptionPane.showMessageDialog(this,
                    "Выберите элемент для удаления",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Получаем текст выбранного узла
        DefaultMutableTreeNode selectedNode =
                (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        String nodeText = selectedNode.toString();

        // Извлекаем имя компонента (без количества)
        String partName = nodeText.split(" \\[x")[0];

        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить '" + partName + "' из спецификации?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                service.deleteSpecItem(currentComponent.getName(), partName);
                loadSpecification();

                JOptionPane.showMessageDialog(this,
                        "Элемент удален",
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
}