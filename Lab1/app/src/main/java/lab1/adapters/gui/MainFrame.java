package lab1.adapters.gui;

import lab1.application.services.SpecificationService;
import lab1.infrastructure.repository.BinaryComponentRepository;
import lab1.infrastructure.repository.BinarySpecRepository;
import lab1.infrastructure.storage.BinaryStorageGateway;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Главное окно программы.
 * Содержит меню для открытия других форм.
 */
public class MainFrame extends JFrame {
    private SpecificationService service;
    private BinaryStorageGateway gateway;
    private BinaryComponentRepository componentRepo;
    private BinarySpecRepository specRepo;

    private JLabel statusLabel;
    private boolean filesOpen = false;

    public MainFrame() {
        setTitle("Многоосевые списки");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 300);
        setLocationRelativeTo(null);

        initComponents();
        initServices();
    }

    /**
     * Инициализация сервисов и репозиториев.
     */
    private void initServices() {
        this.gateway = new BinaryStorageGateway();
        this.componentRepo = new BinaryComponentRepository(gateway);
        this.specRepo = new BinarySpecRepository(gateway, componentRepo);
        this.service = new SpecificationService(componentRepo, specRepo, gateway);
    }

    /**
     * Инициализация компонентов интерфейса.
     */
    private void initComponents() {
        // Главное меню
        JMenuBar menuBar = new JMenuBar();

        // Меню "Открыть"
        JMenu openMenu = new JMenu("Открыть");
        JMenuItem openFileItem = new JMenuItem("Открыть файл...");
        openFileItem.addActionListener(this::openFile);
        openMenu.add(openFileItem);

        JMenuItem createFileItem = new JMenuItem("Создать файл...");
        createFileItem.addActionListener(this::createFile);
        openMenu.add(createFileItem);

        openMenu.addSeparator();

        JMenuItem closeFileItem = new JMenuItem("Закрыть файл");
        closeFileItem.addActionListener(e -> closeFile());
        openMenu.add(closeFileItem);

        JMenuItem exitItem = new JMenuItem("Выход");
        exitItem.addActionListener(e -> System.exit(0));
        openMenu.add(exitItem);

        menuBar.add(openMenu);

        // Меню "Компоненты"
        JMenu componentsMenu = new JMenu("Компоненты");
        JMenuItem showComponentsItem = new JMenuItem("Список компонентов");
        showComponentsItem.addActionListener(e -> showComponentsDialog());
        componentsMenu.add(showComponentsItem);
        menuBar.add(componentsMenu);

        // Меню "Спецификация"
        JMenu specMenu = new JMenu("Спецификация");
        JMenuItem showSpecItem = new JMenuItem("Просмотр спецификации");
        showSpecItem.addActionListener(e -> showSpecificationDialog());
        specMenu.add(showSpecItem);
        menuBar.add(specMenu);

        setJMenuBar(menuBar);

        // Центральная панель с приветствием
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel welcomeLabel = new JLabel("Система управления спецификациями", SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mainPanel.add(welcomeLabel, BorderLayout.CENTER);

        // Панель статуса
        statusLabel = new JLabel("Нет открытых файлов");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        add(mainPanel);

        // Обновляем состояние меню
        updateMenuState();
    }

    /**
     * Открытие файла.
     */
    private void openFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PRD файлы (*.prd)", "prd"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String prdFile = chooser.getSelectedFile().getAbsolutePath();
            String prsFile = prdFile.replace(".prd", ".prs");

            try {
                if (filesOpen) {
                    closeFile();
                }

                service.openFiles(prdFile, prsFile);
                filesOpen = true;
                statusLabel.setText("Открыт файл: " + prdFile);
                updateMenuState();

                JOptionPane.showMessageDialog(this,
                        "Файл успешно открыт",
                        "Успех",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка при открытии файла: " + ex.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Создание нового файла.
     */
    private void createFile(ActionEvent e) {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));

        JTextField prdField = new JTextField(20);
        JTextField nameLengthField = new JTextField("20");
        JTextField prsField = new JTextField(20);

        panel.add(new JLabel("PRD файл:"));
        panel.add(prdField);
        panel.add(new JLabel("Длина имени:"));
        panel.add(nameLengthField);
        panel.add(new JLabel("PRS файл:"));
        panel.add(prsField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Создать новый файл", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String prdFile = prdField.getText().trim();
            String prsFile = prsField.getText().trim();
            int nameLength;

            try {
                nameLength = Integer.parseInt(nameLengthField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Некорректная длина имени",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (prdFile.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Укажите имя PRD файла",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (prsFile.isEmpty()) {
                String baseName = prdFile.contains(".") ?
                        prdFile.substring(0, prdFile.lastIndexOf('.')) : prdFile;
                prsFile = baseName + ".prs";
            }

            try {
                if (filesOpen) {
                    closeFile();
                }

                service.createFiles(prdFile, nameLength, prsFile);
                filesOpen = true;
                statusLabel.setText("Создан файл: " + prdFile);
                updateMenuState();

                JOptionPane.showMessageDialog(this,
                        "Файл успешно создан",
                        "Успех",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка при создании файла: " + ex.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Закрытие текущего файла.
     */
    private void closeFile() {
        if (filesOpen) {
            service.close();
            filesOpen = false;
            statusLabel.setText("Нет открытых файлов");
            updateMenuState();
        }
    }

    /**
     * Показ диалога со списком компонентов.
     */
    private void showComponentsDialog() {
        if (!filesOpen) {
            JOptionPane.showMessageDialog(this,
                    "Сначала откройте файл",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        ComponentsDialog dialog = new ComponentsDialog(this, service, componentRepo);
        dialog.setVisible(true);
    }

    /**
     * Показ диалога со спецификацией.
     */
    private void showSpecificationDialog() {
        if (!filesOpen) {
            JOptionPane.showMessageDialog(this,
                    "Сначала откройте файл",
                    "Предупреждение",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        SpecificationDialog dialog = new SpecificationDialog(this, service, componentRepo, specRepo);
        dialog.setVisible(true);
    }

    /**
     * Обновление состояния меню.
     */
    private void updateMenuState() {
        // Включаем/отключаем пункты меню в зависимости от наличия открытого файла
        // TODO: реализовать при необходимости
    }

    /**
     * Точка входа.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Игнорируем
            }
            new MainFrame().setVisible(true);
        });
    }
}