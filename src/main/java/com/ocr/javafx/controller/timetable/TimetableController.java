package com.ocr.javafx.controller.timetable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ocr.javafx.ApplicationContext;
import com.ocr.javafx.dto.SlotCandidate;
import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.entity.ScheduleSlot;
import com.ocr.javafx.entity.User;
import com.ocr.javafx.enums.ScheduleStatus;
import com.ocr.javafx.enums.ScheduleType;
import com.ocr.javafx.service.AISchedulingService;
import com.ocr.javafx.service.LearningPlanService;
import com.ocr.javafx.service.MailSyncService;
import com.ocr.javafx.service.SlotExtractionService;
import com.ocr.javafx.util.NotificationUtils;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.awt.Desktop;
import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TimetableController {

    private static final DateTimeFormatter HEADER_FMT = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");

    /** Khung giờ hoạt động cố định từ 06:00 sáng đến 23:00 đêm */
    private static final int START_HOUR = SlotExtractionService.START_HOUR;
    private static final int END_HOUR = SlotExtractionService.END_HOUR;

    private static final String TIMETABLE_DIALOG_STYLESHEET = Objects.requireNonNull(
            TimetableController.class.getResource("/com/ocr/javafx/style/timetable-dialog.css"),
            "timetable-dialog.css").toExternalForm();

    private static final double COL_TIME_WIDTH = 64;
    private static final double COL_DAY_MIN_WIDTH = 118;
    private static final double HOUR_ROW_HEIGHT = 76.0;

    @FXML
    private GridPane timetableGrid;

    @FXML
    private BorderPane timetableRoot;

    @FXML
    private ScrollPane timetableScroll;

    @FXML
    private Label monthYearLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    @FXML
    private ComboBox<LearningPlan> planCombo;

    @FXML
    private Spinner<Integer> sessionsSpinner;

    @FXML
    private ComboBox<Integer> durationCombo;

    @FXML
    private Button btnGenerateAi;

    @FXML
    private Button btnSaveDb;

    @FXML
    private Button btnAddManual;

    @FXML
    private HBox draftStatusBox;

    @FXML
    private Label draftStatusLabel;

    @FXML
    private Button btnApplyDraft;

    @FXML
    private Button btnDiscardDraft;

    private final ObservableList<ScheduleSlot> slots = FXCollections.observableArrayList();
    private final List<ScheduleSlot> deletedSlots = new ArrayList<>();
    /**
     * Lưu tạm các slot AI cũ trong tuần bị thay thế bởi bản nháp để khôi phục nếu
     * người dùng bấm "Hủy nháp"
     */
    private final List<ScheduleSlot> stagedReplacedSlots = new ArrayList<>();

    private ApplicationContext applicationContext;
    private LocalDate currentWeekStart;
    private ScheduleSlot draggingSlot;
    private String draggingToken;

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        User user = applicationContext.getSessionManager().getCurrentUser();
        if (user != null && user.getId() != null) {
            LearningPlanService planService = applicationContext.getLearningPlanService();
            planCombo.getItems().setAll(planService.getPlansForDropdown(user.getId()));
            if (!planCombo.getItems().isEmpty()) {
                planCombo.getSelectionModel().selectFirst();
            }
            List<ScheduleSlot> loaded = applicationContext.getScheduleSlotRepository()
                    .findByUserIdOrderByDateAndStart(user.getId());
            for (ScheduleSlot s : loaded) {
                if (s.getStatus() == null) {
                    s.setStatus(ScheduleStatus.PERSISTED);
                }
                if (s.isPersonal() && s.isRecurring() && s.getRecurringEndDate() == null && s.getDate() != null) {
                    s.setRecurringEndDate(s.getDate().plusWeeks(4));
                    applicationContext.getScheduleSlotRepository().save(s);
                }
            }
            slots.setAll(loaded);
        } else {
            planCombo.getItems().clear();
            slots.clear();
        }
        LocalDate today = LocalDate.now();
        currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        renderTimetable();
    }

    @FXML
    public void initialize() {
        currentWeekStart = LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        planCombo.setCellFactory(lv -> new LearningPlanListCell());
        planCombo.setButtonCell(new LearningPlanListCell());

        if (sessionsSpinner != null) {
            SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    1, 14, 3);
            sessionsSpinner.setValueFactory(valueFactory);
            sessionsSpinner.setEditable(true);

            TextField spinnerEditor = sessionsSpinner.getEditor();
            spinnerEditor.setOnAction(ev -> commitSpinnerValue(sessionsSpinner));
            spinnerEditor.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    commitSpinnerValue(sessionsSpinner);
                }
            });
        }

        if (durationCombo != null) {
            durationCombo.setItems(FXCollections.observableArrayList(45, 60, 90, 120, 180));
            durationCombo.setValue(90);
            durationCombo.setEditable(true);
            durationCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Integer mins) {
                    if (mins == null)
                        return "90 phút";
                    return mins + " phút";
                }

                @Override
                public Integer fromString(String s) {
                    return parseDurationMinutes(s);
                }
            });

            TextField comboEditor = durationCombo.getEditor();
            comboEditor.setOnAction(ev -> {
                int parsed = parseDurationMinutes(comboEditor.getText());
                durationCombo.setValue(parsed);
                comboEditor.setText(parsed + " phút");
            });
            comboEditor.focusedProperty().addListener((obs, oldV, hasFocus) -> {
                if (!hasFocus) {
                    int parsed = parseDurationMinutes(comboEditor.getText());
                    durationCombo.setValue(parsed);
                    comboEditor.setText(parsed + " phút");
                }
            });
        }

        slots.addListener((ListChangeListener.Change<? extends ScheduleSlot> c) -> {
            updateDraftBanner();
            renderTimetable();
        });
    }

    private void commitSpinnerValue(Spinner<Integer> spinner) {
        if (spinner == null || !spinner.isEditable()) {
            return;
        }
        String text = spinner.getEditor().getText();
        SpinnerValueFactory<Integer> valueFactory = spinner.getValueFactory();
        if (valueFactory != null) {
            try {
                int val = Integer.parseInt(text.trim());
                val = Math.max(1, Math.min(14, val));
                valueFactory.setValue(val);
                spinner.getEditor().setText(String.valueOf(val));
            } catch (Exception e) {
                spinner.getEditor().setText(String.valueOf(valueFactory.getValue()));
            }
        }
    }

    private static int parseDurationMinutes(String text) {
        if (text == null || text.isBlank()) {
            return 90;
        }
        String s = text.trim().toLowerCase();
        // Hỗ trợ "1h30", "1h 30p", "1h30m", "1 giờ 30 phút"
        java.util.regex.Matcher mHourMin = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:h|giờ|g)\\s*(\\d+)?.*$")
                .matcher(s);
        if (mHourMin.matches()) {
            int hours = Integer.parseInt(mHourMin.group(1));
            int mins = mHourMin.group(2) != null ? Integer.parseInt(mHourMin.group(2)) : 0;
            int total = hours * 60 + mins;
            return Math.max(15, Math.min(480, total));
        }
        // Hỗ trợ dạng số thập phân "1.5h", "2h"
        java.util.regex.Matcher mDecHour = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:h|giờ|g)$")
                .matcher(s);
        if (mDecHour.matches()) {
            double hours = Double.parseDouble(mDecHour.group(1));
            int total = (int) Math.round(hours * 60);
            return Math.max(15, Math.min(480, total));
        }
        // Trích xuất số phút thông thường ví dụ "45", "60 phút", "90p"
        java.util.regex.Matcher mNum = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (mNum.find()) {
            int mins = Integer.parseInt(mNum.group(1));
            return Math.max(15, Math.min(480, mins));
        }
        return 90;
    }

    @FXML
    private void onBackWeekClicked() {
        currentWeekStart = currentWeekStart.minusWeeks(1);
        renderTimetable();
    }

    @FXML
    private void onNextWeekClicked() {
        currentWeekStart = currentWeekStart.plusWeeks(1);
        renderTimetable();
    }

    /**
     * Cơ chế sinh lịch thông minh (Hybrid Slot-Matching + Knowledge Continuity +
     * Live Grid Staging).
     */
    @FXML
    private void onGenerateAi() {
        if (applicationContext == null) {
            return;
        }
        User user = applicationContext.getSessionManager().getCurrentUser();
        if (user == null || user.getId() == null) {
            alert(Alert.AlertType.WARNING, "Đăng nhập", "Cần đăng nhập để tạo lịch.");
            return;
        }
        LearningPlan selectedPlan = planCombo.getSelectionModel().getSelectedItem();
        if (selectedPlan == null) {
            alert(Alert.AlertType.WARNING, "Thiếu Learning Plan",
                    "Vui lòng chọn một Learning Plan trước khi tạo lịch bằng AI.");
            return;
        }

        final int requestedSessions = sessionsSpinner != null && sessionsSpinner.getValue() != null
                ? sessionsSpinner.getValue()
                : 3;
        final int sessionDurationMinutes = durationCombo != null && durationCombo.getValue() != null
                ? durationCombo.getValue()
                : 90;

        final LocalDate weekStart = currentWeekStart;
        final LocalDate weekEnd = currentWeekStart.plusDays(6);

        // Bước 1 (Java Logic): Quét toàn bộ lịch bận trong tuần (bao gồm cả lịch cá
        // nhân lặp tuần)
        final List<ScheduleSlot> busySnapshot = snapshotBusySlotsForWeek(weekStart, weekEnd, selectedPlan);
        SlotExtractionService slotExtractor = applicationContext.getSlotExtractionService();
        final List<SlotCandidate> freeCandidates = slotExtractor.extractFreeSlots(
                weekStart, weekEnd, busySnapshot, sessionDurationMinutes);

        // Kiểm tra số lượng slot trống khả dụng
        if (freeCandidates.isEmpty()) {
            boolean isPastWeek = weekEnd.isBefore(LocalDate.now());
            if (isPastWeek) {
                alert(Alert.AlertType.WARNING, "Tuần trong quá khứ",
                        "Không thể xếp lịch cho tuần đã qua trong quá khứ.\n\n"
                                + "💡 Gợi ý: Vui lòng bấm 'Tuần tới >' để chọn tuần hiện tại hoặc tương lai.");
            } else {
                alert(Alert.AlertType.WARNING, "Không còn khung giờ trống",
                        "Không còn khung giờ trống nào từ thời điểm hiện tại đến hết tuần này!\n\n"
                                + "💡 Gợi ý: Hãy bấm 'Tuần tới >' để AI xếp lịch học trọn vẹn cho tuần sau.");
            }
            return;
        }

        int sessionsToSchedule = requestedSessions;
        if (freeCandidates.size() < requestedSessions) {
            int availableCount = freeCandidates.size();
            ButtonType btnAcceptMax = new ButtonType("Xếp tối đa " + availableCount + " buổi", ButtonBar.ButtonData.OK_DONE);
            ButtonType btnCancel = new ButtonType("Hủy (Sang tuần tới)", ButtonBar.ButtonData.CANCEL_CLOSE);

            Alert confirmAlert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Bạn yêu cầu: " + requestedSessions + " buổi (" + sessionDurationMinutes + " phút/buổi).\n"
                            + "Tuy nhiên, thời gian khả dụng còn lại của tuần này chỉ đủ xếp tối đa " + availableCount + " buổi "
                            + "(các ngày/giờ trước đã trôi qua hoặc bị chiếm bởi lịch cá nhân).\n\n"
                            + "Bạn có muốn AI xếp tối đa " + availableCount + " buổi cho các ngày còn lại của tuần này không?\n"
                            + "(Hoặc bấm 'Hủy' rồi chuyển sang 'Tuần tới >' để xếp trọn vẹn " + requestedSessions + " buổi).",
                    btnAcceptMax, btnCancel
            );
            confirmAlert.setTitle("Không đủ khung giờ trống");
            confirmAlert.setHeaderText("⚡ Chỉ còn " + availableCount + " slot trống khả dụng trong tuần này");
            prepareTimetableAlert(confirmAlert);

            Optional<ButtonType> choice = confirmAlert.showAndWait();
            if (choice.isEmpty() || choice.get() != btnAcceptMax) {
                return;
            }
            sessionsToSchedule = availableCount;
        }

        final int finalSessions = sessionsToSchedule;

        // Thu thập lịch sử bài học từ các tuần trước của cùng Learning Plan để AI dạy
        // nối tiếp
        final List<ScheduleSlot> previousWeeksSlots = collectPreviousWeeksSlotsForPlan(selectedPlan, weekStart);

        setBusy(true);
        final User userRef = user;
        final LearningPlan planRef = selectedPlan;
        final AISchedulingService aiService = applicationContext.getAiSchedulingService();

        Task<List<ScheduleSlot>> task = new Task<>() {
            @Override
            protected List<ScheduleSlot> call() throws Exception {
                return aiService.generateDraftSchedule(
                        planRef,
                        userRef,
                        freeCandidates,
                        finalSessions,
                        previousWeeksSlots);
            }
        };

        task.setOnSucceeded(ev -> {
            try {
                List<ScheduleSlot> createdDrafts = task.getValue();
                if (createdDrafts == null || createdDrafts.isEmpty()) {
                    alert(Alert.AlertType.INFORMATION, "AI", "AI không trả về slot nào trong phản hồi.");
                    return;
                }

                // Khôi phục các slot cũ từng bị ẩn nếu người dùng bấm tạo lại nhiều lần
                if (!stagedReplacedSlots.isEmpty()) {
                    for (ScheduleSlot restored : stagedReplacedSlots) {
                        if (!slots.contains(restored)) {
                            slots.add(restored);
                        }
                    }
                    stagedReplacedSlots.clear();
                }

                // Xóa các bản nháp cũ chưa lưu
                slots.removeIf(ScheduleSlot::isDraft);

                // Tạm ẩn các buổi học chưa hoàn thành của cùng plan trong tuần này để thế chỗ
                // bằng bản nháp mới
                List<ScheduleSlot> oldPlanSlotsInWeek = slots.stream()
                        .filter(s -> isReplaceableStudySlot(s, planRef))
                        .filter(s -> s.getDate() != null
                                && !s.getDate().isBefore(weekStart)
                                && !s.getDate().isAfter(weekEnd))
                        .collect(Collectors.toList());

                stagedReplacedSlots.addAll(oldPlanSlotsInWeek);
                slots.removeAll(oldPlanSlotsInWeek);

                // Render trực tiếp lên lưới ở trạng thái DRAFT (Live Grid Staging, không dùng
                // modal rời)
                slots.addAll(createdDrafts);

                if (finalSessions < requestedSessions) {
                    NotificationUtils.showSuccess("Đã sinh tối đa " + createdDrafts.size()
                            + " buổi học [AI Dự kiến] cho các ngày còn lại trong tuần. Bạn có thể kéo thả, sửa, xóa trước khi bấm Áp dụng!");
                } else {
                    NotificationUtils.showSuccess("Đã sinh " + createdDrafts.size()
                            + " buổi học [AI Dự kiến] trực tiếp trên lưới. Bạn có thể kéo thả, sửa, xóa trước khi bấm Áp dụng!");
                }
            } finally {
                setBusy(false);
                updateDraftBanner();
                renderTimetable();
            }
        });

        task.setOnFailed(ev -> {
            setBusy(false);
            Throwable ex = task.getException();
            if (ex != null) {
                ex.printStackTrace();
            }
            Throwable root = ex != null && ex.getCause() != null ? ex.getCause() : ex;
            String msg = root != null && root.getMessage() != null ? root.getMessage() : "Lỗi không xác định.";
            if (root instanceof JsonProcessingException) {
                alert(Alert.AlertType.ERROR, "AI – JSON", "Không đọc được JSON từ AI: " + msg);
            } else {
                alert(Alert.AlertType.ERROR, "AI", "Không tạo được lịch: " + msg);
            }
            renderTimetable();
        });

        Thread t = new Thread(task, "timetable-openrouter-ai");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Thu thập toàn bộ các buổi học của cùng Learning Plan ở các tuần trước (date <
     * weekStart)
     * từ cả bộ nhớ và CSDL để AI tự động nối tiếp lộ trình kiến thức.
     */
    private List<ScheduleSlot> collectPreviousWeeksSlotsForPlan(LearningPlan plan, LocalDate weekStart) {
        if (plan == null || plan.getId() == null) {
            return Collections.emptyList();
        }
        Long planId = plan.getId();
        Map<String, ScheduleSlot> uniqueByDateAndTime = new LinkedHashMap<>();

        List<ScheduleSlot> dbSlots = applicationContext.getScheduleSlotRepository()
                .findByPlanIdBeforeDate(planId, weekStart);
        for (ScheduleSlot s : dbSlots) {
            if (s.getDate() != null && s.getDate().isBefore(weekStart)) {
                uniqueByDateAndTime.put(s.getDate() + "_" + s.getStartTime(), s);
            }
        }

        for (ScheduleSlot s : slots) {
            if (isSamePlan(s, plan, planId) && s.getDate() != null && s.getDate().isBefore(weekStart)) {
                uniqueByDateAndTime.put(s.getDate() + "_" + s.getStartTime(), s);
            }
        }

        List<ScheduleSlot> result = new ArrayList<>(uniqueByDateAndTime.values());
        result.sort(Comparator.comparing(ScheduleSlot::getDate)
                .thenComparing(s -> s.getStartTime() != null ? s.getStartTime() : LocalTime.MIN));
        return result;
    }

    /**
     * Hủy bỏ toàn bộ các khối DRAFT trên giao diện mà không ảnh hưởng CSDL.
     */
    @FXML
    private void onDiscardDrafts() {
        slots.removeIf(ScheduleSlot::isDraft);
        if (!stagedReplacedSlots.isEmpty()) {
            for (ScheduleSlot restored : stagedReplacedSlots) {
                if (!slots.contains(restored)) {
                    slots.add(restored);
                }
            }
            stagedReplacedSlots.clear();
        }
        updateDraftBanner();
        renderTimetable();
        NotificationUtils.showWarning("Đã hủy bỏ bản nháp AI trên lưới.");
    }

    private void updateDraftBanner() {
        if (draftStatusBox == null || draftStatusLabel == null) {
            return;
        }
        long draftCount = slots.stream().filter(ScheduleSlot::isDraft).count();
        boolean hasDraft = draftCount > 0;
        draftStatusBox.setVisible(hasDraft);
        draftStatusBox.setManaged(hasDraft);
        if (hasDraft) {
            draftStatusLabel.setText("⚡ Đang có " + draftCount + " buổi [AI Dự kiến] trên lưới");
            if (btnApplyDraft != null) {
                btnApplyDraft.setText("✓ Áp dụng & Lưu lịch (" + draftCount + ")");
            }
            if (btnSaveDb != null) {
                btnSaveDb.setVisible(false);
                btnSaveDb.setManaged(false);
            }
        } else {
            if (!stagedReplacedSlots.isEmpty()) {
                for (ScheduleSlot restored : stagedReplacedSlots) {
                    if (!slots.contains(restored)) {
                        slots.add(restored);
                    }
                }
                stagedReplacedSlots.clear();
            }
            if (btnSaveDb != null) {
                btnSaveDb.setVisible(true);
                btnSaveDb.setManaged(true);
                btnSaveDb.setText("💾 Lưu lịch");
            }
        }
    }

    private static boolean isSamePlan(ScheduleSlot slot, LearningPlan selectedPlan, Long selectedPlanId) {
        if (slot == null || slot.getPlan() == null) {
            return false;
        }
        Long planId = slot.getPlan().getId();
        if (selectedPlanId == null || planId == null) {
            return slot.getPlan() == selectedPlan;
        }
        return Objects.equals(planId, selectedPlanId);
    }

    private void markDeletedIfPersisted(ScheduleSlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        boolean exists = deletedSlots.stream()
                .anyMatch(s -> s.getId() != null && Objects.equals(s.getId(), slot.getId()));
        if (!exists) {
            deletedSlots.add(slot);
        }
    }

    private static String planDisplayName(LearningPlan plan) {
        if (plan.getTitle() != null && !plan.getTitle().isBlank()) {
            return plan.getTitle().trim();
        }
        if (plan.getGoal() != null && !plan.getGoal().isBlank()) {
            String g = plan.getGoal().trim();
            return g.length() > 120 ? g.substring(0, 117) + "…" : g;
        }
        return "Plan #" + plan.getId();
    }

    /**
     * Lấy toàn bộ các slot bận trong tuần (gồm lịch cá nhân trong tuần, lịch cá
     * nhân lặp lại từ tuần khác,
     * và các slot AI đã hoàn thành hoặc thuộc plan khác).
     */
    private List<ScheduleSlot> snapshotBusySlotsForWeek(LocalDate weekStart, LocalDate weekEnd,
            LearningPlan selectedPlan) {
        return slots.stream()
                .filter(s -> !s.isDraft())
                .filter(s -> s.getDate() != null && s.getStartTime() != null && s.getEndTime() != null)
                .filter(s -> {
                    boolean inCurrentWeek = !s.getDate().isBefore(weekStart) && !s.getDate().isAfter(weekEnd);
                    if (inCurrentWeek) {
                        return !s.isDateExcluded(s.getDate());
                    }
                    if (s.isPersonal() && s.isRecurring()) {
                        LocalDate candidate = weekStart.plusDays(s.getDate().getDayOfWeek().getValue() - 1L);
                        return !candidate.isBefore(s.getDate())
                                && (s.getRecurringEndDate() == null || !candidate.isAfter(s.getRecurringEndDate()))
                                && !s.isDateExcluded(candidate);
                    }
                    return false;
                })
                .filter(s -> !isReplaceableStudySlot(s, selectedPlan))
                .collect(Collectors.toList());
    }

    private static boolean isReplaceableStudySlot(ScheduleSlot slot, LearningPlan selectedPlan) {
        return slot.isAiPlan()
                && !slot.isCompleted()
                && isSamePlan(slot, selectedPlan, selectedPlan.getId());
    }

    @FXML
    private void onSaveToDb() {
        if (applicationContext == null) {
            return;
        }

        // Khi xác nhận lưu, các slot cũ bị thay thế bởi bản nháp sẽ được đưa vào danh
        // sách xóa khỏi DB
        for (ScheduleSlot replaced : stagedReplacedSlots) {
            markDeletedIfPersisted(replaced);
        }
        stagedReplacedSlots.clear();

        for (ScheduleSlot s : slots) {
            s.setStatus(ScheduleStatus.PERSISTED);
        }

        List<ScheduleSlot> slotsToSave = new ArrayList<>(slots);
        List<ScheduleSlot> slotsToDelete = new ArrayList<>(deletedSlots);

        try {
            applicationContext.getScheduleSlotRepository()
                    .saveChanges(slotsToSave, slotsToDelete);

            deletedSlots.clear();

            Set<Long> planIdsToUpdate = new HashSet<>();
            for (ScheduleSlot slot : slotsToSave) {
                if (slot.getPlan() != null && slot.getPlan().getId() != null) {
                    planIdsToUpdate.add(slot.getPlan().getId());
                }
            }
            for (ScheduleSlot slot : slotsToDelete) {
                if (slot.getPlan() != null && slot.getPlan().getId() != null) {
                    planIdsToUpdate.add(slot.getPlan().getId());
                }
            }
            for (Long planId : planIdsToUpdate) {
                applicationContext.getLearningPlanService().calculateAndUpdateProgress(planId);
            }

            User currentUser = applicationContext.getSessionManager() != null 
                    ? applicationContext.getSessionManager().getCurrentUser() 
                    : null;
            CompletableFuture.runAsync(() -> {
                try {
                    String email = (currentUser != null && currentUser.getEmail() != null) 
                            ? currentUser.getEmail() 
                            : "";
                    MailSyncService.getInstance().syncSlots(slotsToSave, slotsToDelete, email);
                } catch (Exception ex) {
                    System.err.println("Mail sync error: " + ex.getMessage());
                }
            });

            updateDraftBanner();
            renderTimetable();

            alert(Alert.AlertType.INFORMATION, "Đã lưu",
                    "Đã áp dụng và lưu toàn bộ lịch xuống cơ sở dữ liệu thành công.");
        } catch (Exception e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Lỗi lưu",
                    e.getMessage() != null ? e.getMessage() : "Không lưu được lịch.");
        }
    }

    private String formatToIcalDateTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
    }

    @FXML
    private void onExportIcs() {
        if (slots.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Không có dữ liệu", "Không có lịch học để xuất file .ics.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu lịch học (.ics)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("iCalendar Files", "*.ics"));
        chooser.setInitialFileName("LichHoc_AI.ics");

        Window owner = timetableGrid != null && timetableGrid.getScene() != null
                ? timetableGrid.getScene().getWindow()
                : null;
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }

        String dtStampUtc = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) + "Z";

        int exportedEvents = 0;
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            writer.println("BEGIN:VCALENDAR");
            writer.println("VERSION:2.0");
            writer.println("PRODID:-//AI Timetable//O-Range//VN");

            for (ScheduleSlot slot : slots) {
                if (slot == null || slot.getDate() == null || slot.getStartTime() == null
                        || slot.getEndTime() == null) {
                    continue;
                }
                String uid = slot.getId() != null ? String.valueOf(slot.getId()) : UUID.randomUUID().toString();
                String start = formatToIcalDateTime(slot.getDate(), slot.getStartTime());
                String end = formatToIcalDateTime(slot.getDate(), slot.getEndTime());
                String topic = escapeIcsText(slot.getTopic());
                String subTopic = escapeIcsText(slot.getSubTopic());

                writer.println("BEGIN:VEVENT");
                writer.println("UID:" + uid + "@aitimetable.com");
                writer.println("DTSTAMP:" + dtStampUtc);
                writer.println("DTSTART:" + start);
                writer.println("DTEND:" + end);
                writer.println("SUMMARY:" + topic);
                writer.println("DESCRIPTION:" + subTopic);
                if (slot.isPersonal() && slot.isRecurring()) {
                    writer.println("RRULE:FREQ=WEEKLY");
                }
                writer.println("END:VEVENT");
                exportedEvents++;
            }

            writer.println("END:VCALENDAR");
            alert(Alert.AlertType.INFORMATION, "Xuất .ics thành công",
                    "Đã xuất " + exportedEvents + " sự kiện ra file:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Xuất .ics thất bại",
                    e.getMessage() != null ? e.getMessage() : "Không thể ghi file .ics.");
        }
    }

    private static String escapeIcsText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Mở hộp thoại thêm mới sự kiện (hỗ trợ cả Lịch cá nhân lặp tuần và Buổi học
     * AI, kèm cấu hình Email Reminder).
     */
    @FXML
    private void onAddManual() {
        if (applicationContext == null) {
            return;
        }
        User user = applicationContext.getSessionManager().getCurrentUser();
        if (user == null || user.getId() == null) {
            alert(Alert.AlertType.WARNING, "Đăng nhập", "Cần đăng nhập.");
            return;
        }

        ScheduleSlot newSlot = new ScheduleSlot();
        newSlot.setDate(currentWeekStart);
        newSlot.setStartTime(LocalTime.of(8, 0));
        newSlot.setEndTime(LocalTime.of(9, 30));
        newSlot.setScheduleType(ScheduleType.PERSONAL);
        newSlot.setStatus(ScheduleStatus.PERSISTED);
        newSlot.setRecurring(false);
        newSlot.setUser(user);
        newSlot.setPlan(planCombo.getSelectionModel().getSelectedItem());
        newSlot.setReminderEnabled(false);
        newSlot.setReminderMinutes(15);
        newSlot.setReminderMessage("");

        openSlotEditorDialog(newSlot, true);
    }

    /**
     * Hộp thoại hợp nhất cho Thêm mới & Chỉnh sửa sự kiện (Personal / AI Plan, Lặp
     * tuần, Email Reminder, Conflict Resolution).
     */
    private void openSlotEditorDialog(ScheduleSlot slot, boolean isNew) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Thêm sự kiện / Lịch học mới" : "Chỉnh sửa chi tiết sự kiện");
        dialog.setHeaderText(
                isNew ? "Thiết lập thông tin sự kiện trên thời khóa biểu" : "Cập nhật thông tin & nhắc nhở sự kiện");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        boolean isPersonal = slot.isPersonal() || slot.getScheduleType() == null;

        Label typeLabel = new Label(isPersonal ? "📌 Lịch cá nhân (Cố định)" : "🎓 Kế hoạch học tập AI (Cố định)");
        typeLabel.setStyle(isPersonal
                ? "-fx-font-weight: bold; -fx-text-fill: #b45309; -fx-background-color: #fef3c7; -fx-padding: 4 8; -fx-background-radius: 6;"
                : "-fx-font-weight: bold; -fx-text-fill: #1d4ed8; -fx-background-color: #dbeafe; -fx-padding: 4 8; -fx-background-radius: 6;");

        LocalDate displayDate;
        if (isNew) {
            displayDate = slot.getDate() != null ? slot.getDate() : currentWeekStart;
        } else if (slot.isPersonal() && slot.isRecurring()) {
            displayDate = slot.getDate();
        } else {
            displayDate = resolveDateForCurrentWeek(slot);
            if (displayDate == null) {
                displayDate = slot.getDate() != null ? slot.getDate() : currentWeekStart;
            }
        }
        DatePicker datePicker = new DatePicker(displayDate);
        TextField startField = new TextField(
                slot.getStartTime() != null ? slot.getStartTime().format(TIME_FMT) : "8:00");
        TextField endField = new TextField(slot.getEndTime() != null ? slot.getEndTime().format(TIME_FMT) : "9:30");

        TextField topicField = new TextField(slot.getTopic() != null ? slot.getTopic() : "");
        topicField.setPromptText(isPersonal ? "VD: Học trên trường, Họp CLB, Lịch làm thêm..." : "Chủ đề bài học...");

        TextField subTopicField = new TextField(slot.getSubTopic() != null ? slot.getSubTopic() : "");
        subTopicField.setPromptText("Mục tiêu chi tiết / Nội dung công việc");

        TextField resourceField = new TextField(slot.getResourceUrl() != null ? slot.getResourceUrl() : "");
        resourceField.setPromptText("Ghi chú tài liệu hoặc đường dẫn (https://...)");

        CheckBox recurringCheck = new CheckBox("Lặp lại hàng tuần (Tự động hiển thị ở các tuần tiếp theo)");
        recurringCheck.setSelected(slot.isRecurring());

        LocalDate initialRecurringEnd = slot.getRecurringEndDate();
        if (initialRecurringEnd == null && slot.isRecurring() && displayDate != null) {
            initialRecurringEnd = displayDate.plusWeeks(4);
        }
        DatePicker recurringEndPicker = new DatePicker(initialRecurringEnd);
        recurringEndPicker.setPromptText("Bắt buộc chọn ngày kết thúc");
        recurringEndPicker.setDisable(!recurringCheck.isSelected());
        recurringCheck.selectedProperty().addListener((obs, oldV, isSel) -> {
            recurringEndPicker.setDisable(!isSel);
            commitDatePicker(datePicker);
            if (isSel && recurringEndPicker.getValue() == null && datePicker.getValue() != null) {
                recurringEndPicker.setValue(datePicker.getValue().plusWeeks(4));
            }
        });
        datePicker.getEditor().focusedProperty().addListener((obs, oldV, hasFocus) -> {
            if (!hasFocus) {
                commitDatePicker(datePicker);
            }
        });
        recurringEndPicker.getEditor().focusedProperty().addListener((obs, oldV, hasFocus) -> {
            if (!hasFocus) {
                commitDatePicker(recurringEndPicker);
            }
        });
        HBox recurringEndBox = labeled("Kết thúc lặp (*)", recurringEndPicker);

        CheckBox completedCheck = new CheckBox("Đánh dấu đã hoàn thành");
        completedCheck.setSelected(slot.isCompleted());

        String resourceUrl = slot.getResourceUrl() != null ? slot.getResourceUrl().trim() : "";
        Hyperlink openUrlBtn = new Hyperlink(
                resourceUrl.startsWith("http") ? "🔗 Mở liên kết tài liệu trên trình duyệt" : "");
        openUrlBtn.setVisible(resourceUrl.startsWith("http"));
        openUrlBtn.setManaged(resourceUrl.startsWith("http"));
        openUrlBtn.setOnAction(ev -> openExternalUrl(resourceField.getText().trim()));

        // Cấu hình Lời nhắc qua Email (Áp dụng linh hoạt cho cả AI Plan & Lịch cá nhân)
        CheckBox reminderCheck = new CheckBox("🔔 Bật lời nhắc qua Email");
        reminderCheck.setStyle("-fx-font-weight: bold; -fx-text-fill: #ea580c;");
        reminderCheck.setSelected(slot.isReminderEnabled());

        ComboBox<Integer> reminderMinutesCombo = new ComboBox<>();
        reminderMinutesCombo.getItems().addAll(5, 10, 15, 30, 60);
        reminderMinutesCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer val) {
                if (val == null) return "";
                if (val == 60) return "Trước 1 tiếng";
                return "Trước " + val + " phút";
            }
            @Override
            public Integer fromString(String str) {
                return 15;
            }
        });
        reminderMinutesCombo.setValue(slot.getReminderMinutes());

        TextField reminderMsgField = new TextField(slot.getReminderMessage() != null ? slot.getReminderMessage() : "");
        reminderMsgField.setPromptText("Tùy chỉnh nội dung nhắc (để trống sẽ dùng mặc định)");

        reminderMinutesCombo.setDisable(!reminderCheck.isSelected());
        reminderMsgField.setDisable(!reminderCheck.isSelected());
        reminderCheck.selectedProperty().addListener((obs, oldV, isSel) -> {
            reminderMinutesCombo.setDisable(!isSel);
            reminderMsgField.setDisable(!isSel);
        });

        VBox reminderBox = new VBox(6,
                reminderCheck,
                labeled("Thời gian nhắc", reminderMinutesCombo),
                labeled("Nội dung lời nhắc", reminderMsgField)
        );
        reminderBox.setStyle("-fx-background-color: #fff7ed; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #fed7aa; -fx-border-radius: 8;");

        VBox form = new VBox(10);
        form.getChildren().addAll(
                labeled("Phân loại", typeLabel),
                labeled("Ngày", datePicker),
                labeled("Bắt đầu (H:mm)", startField),
                labeled("Kết thúc (H:mm)", endField),
                labeled("Tên / Chủ đề", topicField),
                labeled("Mục tiêu / Chi tiết", subTopicField),
                labeled("Tài liệu / URL", resourceField),
                openUrlBtn,
                new Separator(),
                reminderBox,
                new Separator());

        if (isPersonal) {
            form.getChildren().addAll(recurringCheck, recurringEndBox);
        } else {
            if (slot.getPlan() != null) {
                Label planLbl = new Label("Gắn với: " + planDisplayName(slot.getPlan()));
                planLbl.setStyle("-fx-font-style: italic; -fx-text-fill: #4b5563;");
                form.getChildren().addAll(planLbl);
            }
            form.getChildren().addAll(completedCheck);
        }

        form.setPadding(new Insets(16));
        form.setPrefWidth(480);
        form.getStyleClass().add("timetable-dialog-form");
        dialog.getDialogPane().setContent(form);
        prepareTimetableDialog(dialog, "form");

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            commitDatePicker(datePicker);
            commitDatePicker(recurringEndPicker);
            if (!validateSlotForm(datePicker, startField, endField, topicField)) {
                ev.consume();
                alert(Alert.AlertType.WARNING, "Dữ liệu chưa hợp lệ",
                        "Vui lòng nhập Tên sự kiện và khung giờ hợp lệ trong khoảng 06:00 – 23:00 (định dạng H:mm, giờ kết thúc sau giờ bắt đầu).");
                return;
            }
            if (isPersonal && recurringCheck.isSelected()) {
                if (recurringEndPicker.getValue() == null) {
                    ev.consume();
                    alert(Alert.AlertType.WARNING, "Bắt buộc chọn Ngày kết thúc lặp",
                            "Khi chọn lặp lại hàng tuần, bạn bắt buộc phải nhập Ngày kết thúc lặp (không được để trống).");
                    return;
                }
                if (recurringEndPicker.getValue().isBefore(datePicker.getValue())) {
                    ev.consume();
                    alert(Alert.AlertType.WARNING, "Ngày kết thúc không hợp lệ",
                            "Ngày kết thúc lặp lại không thể trước ngày bắt đầu sự kiện (" + datePicker.getValue()
                                    + ").");
                    return;
                }
            }
        });

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }

        commitDatePicker(datePicker);
        commitDatePicker(recurringEndPicker);
        LocalDate newDate = datePicker.getValue();
        LocalTime newStart = parseTimeFlexible(startField.getText().trim());
        LocalTime newEnd = parseTimeFlexible(endField.getText().trim());

        // Kiểm tra xung đột
        List<ScheduleSlot> overlapping = findOverlappingSlots(slot, newDate, newStart, newEnd);
        if (!overlapping.isEmpty()) {
            List<ScheduleSlot> personalConflicts = overlapping.stream()
                    .filter(ScheduleSlot::isPersonal)
                    .toList();
            List<ScheduleSlot> aiConflicts = overlapping.stream()
                    .filter(ScheduleSlot::isAiPlan)
                    .toList();

            if (!personalConflicts.isEmpty() || !isPersonal) {
                alert(Alert.AlertType.WARNING, "Trùng lịch",
                        "Khung giờ đã chọn bị trùng với sự kiện khác trên thời khóa biểu.");
                return;
            }

            // Trường hợp Lịch cá nhân đè lên Buổi học AI -> Kích hoạt Conflict Resolution
            if (!aiConflicts.isEmpty()) {
                boolean resolved = resolvePersonalOverAiConflict(aiConflicts, newDate, newStart, newEnd);
                if (!resolved) {
                    return;
                }
            }
        }

        slot.setDate(newDate);
        slot.setStartTime(newStart);
        slot.setEndTime(newEnd);
        slot.setTopic(topicField.getText().trim());
        slot.setSubTopic(subTopicField.getText() != null ? subTopicField.getText().trim() : "");
        slot.setResourceUrl(resourceField.getText() != null ? resourceField.getText().trim() : "");
        slot.setRecurring(isPersonal && recurringCheck.isSelected());
        slot.setRecurringEndDate(isPersonal && recurringCheck.isSelected() ? recurringEndPicker.getValue() : null);
        slot.setCompleted(!isPersonal && completedCheck.isSelected());
        slot.setReminderEnabled(reminderCheck.isSelected());
        slot.setReminderMinutes(reminderMinutesCombo.getValue() != null ? reminderMinutesCombo.getValue() : 15);
        slot.setReminderMessage(reminderMsgField.getText() != null ? reminderMsgField.getText().trim() : "");

        if (isPersonal) {
            slot.setScheduleType(ScheduleType.PERSONAL);
            slot.setPlan(null);
            slot.setStatus(ScheduleStatus.PERSISTED);
        } else {
            slot.setScheduleType(ScheduleType.AI_PLAN);
            if (slot.getPlan() == null) {
                slot.setPlan(planCombo.getSelectionModel().getSelectedItem());
            }
        }

        if (isNew) {
            slots.add(slot);
        }

        // Tự động lưu ngay xuống CSDL đối với các sự kiện đã lưu / lịch cá nhân để không bị mất ngày kết thúc lặp khi chuyển màn hình
        if (!slot.isDraft() && applicationContext != null) {
            applicationContext.getScheduleSlotRepository().save(slot);
            if (slot.getPlan() != null && slot.getPlan().getId() != null) {
                applicationContext.getLearningPlanService().calculateAndUpdateProgress(slot.getPlan().getId());
            }
            User currentUser = applicationContext.getSessionManager() != null
                    ? applicationContext.getSessionManager().getCurrentUser()
                    : null;
            CompletableFuture.runAsync(() -> {
                try {
                    String email = (currentUser != null && currentUser.getEmail() != null)
                            ? currentUser.getEmail()
                            : "";
                    MailSyncService.getInstance().syncSlots(List.of(slot), Collections.emptyList(), email);
                } catch (Exception ignored) {
                }
            });
        }

        updateDraftBanner();
        renderTimetable();
    }

    private static void commitDatePicker(DatePicker picker) {
        if (picker == null || picker.getEditor() == null) {
            return;
        }
        String text = picker.getEditor().getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        try {
            StringConverter<LocalDate> converter = picker.getConverter();
            if (converter != null) {
                LocalDate parsed = converter.fromString(text.trim());
                if (parsed != null) {
                    picker.setValue(parsed);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Xử lý xung đột thông minh khi người dùng thêm hoặc dời một Lịch cá nhân
     * (PERSONAL)
     * đè trúng vào một hoặc nhiều buổi học AI (AI_PLAN).
     *
     * @return {@code true} nếu xung đột đã được giải quyết và cho phép đặt lịch cá
     *         nhân vào vị trí mới.
     */
    private boolean resolvePersonalOverAiConflict(
            List<ScheduleSlot> conflictedAiSlots,
            LocalDate personalDate,
            LocalTime personalStart,
            LocalTime personalEnd) {
        String aiNames = conflictedAiSlots.stream()
                .map(s -> "• " + (s.getTopic() != null ? s.getTopic() : "Buổi học AI")
                        + " (" + s.getStartTime() + " – " + s.getEndTime() + ")")
                .collect(Collectors.joining("\n"));

        ButtonType btnRelocateAi = new ButtonType("Dời buổi học AI sang slot trống gần nhất", ButtonBar.ButtonData.YES);
        ButtonType btnDeleteAi = new ButtonType("Xóa buổi học AI bị trùng", ButtonBar.ButtonData.NO);
        ButtonType btnCancel = new ButtonType("Hủy thao tác", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert conflictAlert = new Alert(
                Alert.AlertType.WARNING,
                "Lịch cá nhân bạn vừa chọn trùng vào buổi học AI:\n" + aiNames
                        + "\n\nBạn muốn giữ lịch cá nhân và xử lý buổi học AI này như thế nào?",
                btnRelocateAi, btnDeleteAi, btnCancel);
        conflictAlert.setTitle("Cảnh báo xung đột lịch học");
        conflictAlert.setHeaderText("⚠️ Phát hiện trùng giờ với Kế hoạch học tập AI!");
        prepareTimetableAlert(conflictAlert);

        Optional<ButtonType> choice = conflictAlert.showAndWait();
        if (choice.isEmpty() || choice.get() == btnCancel) {
            return false;
        }

        if (choice.get() == btnDeleteAi) {
            for (ScheduleSlot aiSlot : conflictedAiSlots) {
                markDeletedIfPersisted(aiSlot);
                slots.remove(aiSlot);
            }
            NotificationUtils.showWarning("Đã xóa buổi học AI bị trùng để nhường chỗ cho lịch cá nhân.");
            return true;
        }

        // Phương án 1: Tự động tìm slot trống gần nhất trong tuần cho từng buổi học AI
        // bị đè
        LocalDate weekStart = currentWeekStart;
        LocalDate weekEnd = currentWeekStart.plusDays(6);

        // Giả lập slot cá nhân mới vào danh sách bận để không dời AI trúng lại chính nó
        ScheduleSlot tempPersonal = new ScheduleSlot();
        tempPersonal.setScheduleType(ScheduleType.PERSONAL);
        tempPersonal.setDate(personalDate);
        tempPersonal.setStartTime(personalStart);
        tempPersonal.setEndTime(personalEnd);

        List<ScheduleSlot> occupied = new ArrayList<>();
        for (ScheduleSlot s : slots) {
            if (!conflictedAiSlots.contains(s)) {
                occupied.add(s);
            }
        }
        occupied.add(tempPersonal);

        SlotExtractionService extractor = applicationContext.getSlotExtractionService();
        List<String> movedSummary = new ArrayList<>();

        for (ScheduleSlot aiSlot : conflictedAiSlots) {
            int durationMins = (int) Math.max(30,
                    Duration.between(aiSlot.getStartTime(), aiSlot.getEndTime()).toMinutes());
            Optional<SlotCandidate> nearest = extractor.findNearestFreeSlot(
                    aiSlot.getDate(),
                    aiSlot.getStartTime(),
                    durationMins,
                    weekStart,
                    weekEnd,
                    occupied);
            if (nearest.isEmpty()) {
                alert(Alert.AlertType.ERROR, "Không còn slot trống",
                        "Tuần này không còn khung giờ trống nào đủ " + durationMins + " phút để dời buổi học AI '"
                                + aiSlot.getTopic() + "'. Vui lòng tự sắp xếp lại.");
                return false;
            }
            SlotCandidate target = nearest.get();
            aiSlot.setDate(target.date());
            aiSlot.setStartTime(target.startTime());
            aiSlot.setEndTime(target.endTime());
            occupied.add(aiSlot);
            movedSummary.add("'" + aiSlot.getTopic() + "' ➔ " + target.displayText());
        }

        NotificationUtils.showSuccess("Đã tự động dời lịch AI: " + String.join("; ", movedSummary));
        return true;
    }

    private static boolean validateSlotForm(DatePicker datePicker, TextField startField,
            TextField endField, TextField topicField) {
        if (datePicker.getValue() == null) {
            return false;
        }
        if (topicField.getText() == null || topicField.getText().trim().isEmpty()) {
            return false;
        }
        try {
            LocalTime start = parseTimeFlexible(startField.getText().trim());
            LocalTime end = parseTimeFlexible(endField.getText().trim());
            if (!end.isAfter(start)) {
                return false;
            }
            if (start.getHour() < START_HOUR || end.isAfter(LocalTime.of(END_HOUR, 59))) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static LocalTime parseTimeFlexible(String s) {
        try {
            return LocalTime.parse(s);
        } catch (Exception e1) {
            try {
                return LocalTime.parse(s, DateTimeFormatter.ofPattern("H:mm"));
            } catch (Exception e2) {
                return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
    }

    private static HBox labeled(String title, javafx.scene.Node node) {
        Label l = new Label(title);
        l.setMinWidth(115);
        HBox row = new HBox(10, l, node);
        row.getStyleClass().add("timetable-dialog-field-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(node, Priority.ALWAYS);
        return row;
    }

    /**
     * Tính ngày hiển thị của một slot trong tuần đang xem (hỗ trợ chiếu các slot
     * PERSONAL có isRecurring = true).
     */
    private LocalDate resolveDateForCurrentWeek(ScheduleSlot slot) {
        if (slot == null || slot.getDate() == null) {
            return null;
        }
        LocalDate end = currentWeekStart.plusDays(6);
        if (!slot.getDate().isBefore(currentWeekStart) && !slot.getDate().isAfter(end)) {
            if (slot.isDateExcluded(slot.getDate())) {
                return null;
            }
            return slot.getDate();
        }
        if (slot.isPersonal() && slot.isRecurring()) {
            LocalDate candidate = currentWeekStart.plusDays(slot.getDate().getDayOfWeek().getValue() - 1L);
            // Tuyệt đối không hiển thị ở các tuần trong quá khứ trước ngày bắt đầu sự kiện
            if (candidate.isBefore(slot.getDate())) {
                return null;
            }
            // Không hiển thị sau ngày kết thúc lặp lại (nếu có cấu hình)
            if (slot.getRecurringEndDate() != null && candidate.isAfter(slot.getRecurringEndDate())) {
                return null;
            }
            // Không hiển thị nếu ngày candidate nằm trong danh sách loại trừ của chuỗi lặp
            if (slot.isDateExcluded(candidate)) {
                return null;
            }
            return candidate;
        }
        return null;
    }

    /**
     * Vẽ lưới tuần hiện tại (06:00 – 23:00) và hiển thị thống nhất cả Lịch cá nhân,
     * Lịch AI chính thức và Bản nháp DRAFT.
     */
    public void renderTimetable() {
        if (timetableGrid == null || monthYearLabel == null) {
            return;
        }
        timetableGrid.getChildren().clear();
        timetableGrid.getRowConstraints().clear();
        timetableGrid.getColumnConstraints().clear();
        timetableGrid.getStyleClass().remove("calendar-grid");
        timetableGrid.getStyleClass().add("calendar-grid");

        ColumnConstraints timeCol = new ColumnConstraints(COL_TIME_WIDTH, COL_TIME_WIDTH, COL_TIME_WIDTH);
        timeCol.setHgrow(Priority.NEVER);
        timetableGrid.getColumnConstraints().add(timeCol);

        javafx.beans.binding.DoubleBinding dayWidthBinding = javafx.beans.binding.Bindings.createDoubleBinding(
                () -> {
                    double scrollW = timetableScroll != null ? timetableScroll.getViewportBounds().getWidth() : 0;
                    if (scrollW <= 0 && timetableScroll != null) {
                        scrollW = timetableScroll.getWidth() - 20;
                    }
                    double rem = Math.max(7 * COL_DAY_MIN_WIDTH, scrollW - COL_TIME_WIDTH - 6.0);
                    return rem / 7.0;
                },
                timetableScroll != null ? timetableScroll.viewportBoundsProperty() : timetableGrid.widthProperty(),
                timetableScroll != null ? timetableScroll.widthProperty() : timetableGrid.widthProperty());

        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setMinWidth(COL_DAY_MIN_WIDTH);
            dayCol.prefWidthProperty().bind(dayWidthBinding);
            dayCol.setHgrow(Priority.ALWAYS);
            timetableGrid.getColumnConstraints().add(dayCol);
        }
        timetableGrid.setMinWidth(COL_TIME_WIDTH + 7 * COL_DAY_MIN_WIDTH);
        timetableGrid.setMaxWidth(Double.MAX_VALUE);
        timetableGrid.setMaxHeight(Double.MAX_VALUE);

        RowConstraints headerRow = new RowConstraints(56, 64, 80);
        timetableGrid.getRowConstraints().add(headerRow);

        LocalDate weekEnd = currentWeekStart.plusDays(6);
        monthYearLabel.setText("Tuần: " + currentWeekStart.format(HEADER_FMT) + " – " + weekEnd.format(HEADER_FMT));

        for (int i = 0; i < 7; i++) {
            LocalDate date = currentWeekStart.plusDays(i);
            VBox headerBox = new VBox(2);
            headerBox.getStyleClass().add("day-header");
            String dayName = date.getDayOfWeek().name();
            Label lblDay = new Label(dayName.charAt(0) + dayName.substring(1).toLowerCase());
            lblDay.getStyleClass().add("day-name");
            Label lblDate = new Label(date.format(DateTimeFormatter.ofPattern("EEE d/M")));
            lblDate.getStyleClass().add("day-date");
            headerBox.getChildren().addAll(lblDay, lblDate);
            timetableGrid.add(headerBox, i + 1, 0);
        }

        int rowCount = END_HOUR - START_HOUR + 1;
        for (int row = 1; row <= rowCount; row++) {
            int hour = START_HOUR + row - 1;
            RowConstraints rowConst = new RowConstraints(HOUR_ROW_HEIGHT, HOUR_ROW_HEIGHT, HOUR_ROW_HEIGHT);
            rowConst.setValignment(javafx.geometry.VPos.TOP);
            rowConst.setVgrow(Priority.NEVER);
            timetableGrid.getRowConstraints().add(rowConst);

            Label timeLabel = new Label(String.format("%02d:00", hour));
            timeLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            timeLabel.getStyleClass().add("time-cell");
            timetableGrid.add(timeLabel, 0, row);

            for (int col = 1; col <= 7; col++) {
                Label emptyCell = new Label("-");
                emptyCell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                emptyCell.getStyleClass().add("empty-cell");
                emptyCell.setUserData(new DropCell(col, hour));
                wireDropTarget(emptyCell);
                timetableGrid.add(emptyCell, col, row);
            }
        }

        double vgap = timetableGrid.getVgap();
        double rowPitch = HOUR_ROW_HEIGHT + vgap;

        for (ScheduleSlot slot : slots) {
            if (slot.getStartTime() == null || slot.getEndTime() == null) {
                continue;
            }
            LocalDate effectiveDate = resolveDateForCurrentWeek(slot);
            if (effectiveDate == null) {
                continue;
            }
            int startHour = slot.getStartTime().getHour();
            int startMin = slot.getStartTime().getMinute();
            if (startHour < START_HOUR || startHour > END_HOUR) {
                continue;
            }
            int colIndex = effectiveDate.getDayOfWeek().getValue();
            int rowIndex = startHour - START_HOUR + 1;
            long minutes = Math.max(30, Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes());

            int endHourVal = slot.getEndTime().getHour();
            int endMinVal = slot.getEndTime().getMinute();
            int lastTouchedHour = (endMinVal > 0) ? endHourVal : Math.max(startHour, endHourVal - 1);
            lastTouchedHour = Math.min(END_HOUR, Math.max(startHour, lastTouchedHour));
            int spanHours = Math.max(1, lastTouchedHour - startHour + 1);
            if (rowIndex + spanHours - 1 > rowCount) {
                spanHours = Math.max(1, rowCount - rowIndex + 1);
            }

            double topOffset = (startMin / 60.0) * rowPitch + 2.0;
            double cardHeight = Math.max(44.0, (minutes / 60.0) * rowPitch - 4.0);

            VBox card = buildSlotCard(slot, minutes);
            card.setMaxWidth(Double.MAX_VALUE);
            card.setMinHeight(cardHeight);
            card.setPrefHeight(cardHeight);
            card.setMaxHeight(cardHeight);
            GridPane.setFillWidth(card, true);
            GridPane.setFillHeight(card, false);
            GridPane.setValignment(card, javafx.geometry.VPos.TOP);
            GridPane.setMargin(card, new Insets(topOffset, 4, 2, 4));
            timetableGrid.add(card, colIndex, rowIndex, 1, spanHours);
        }
    }

    private record PlanColorTheme(String bgGradient, String borderColor, String badgeBg, String topicColor) {
    }

    private static final List<PlanColorTheme> PLAN_COLOR_THEMES = List.of(
            new PlanColorTheme("linear-gradient(to bottom right, #e0f2fe 0%, #dbeafe 100%)", "#0284c7", "#0284c7", "#0c4a6e"),
            new PlanColorTheme("linear-gradient(to bottom right, #f3e8ff 0%, #ede9fe 100%)", "#7e22ce", "#7e22ce", "#3b0764"),
            new PlanColorTheme("linear-gradient(to bottom right, #ccfbf1 0%, #cffafe 100%)", "#0d9488", "#0f766e", "#134e4a"),
            new PlanColorTheme("linear-gradient(to bottom right, #ffe4e6 0%, #fce7f3 100%)", "#e11d48", "#be123c", "#881337"),
            new PlanColorTheme("linear-gradient(to bottom right, #e0e7ff 0%, #dbeafe 100%)", "#4f46e5", "#4338ca", "#1e1b4b"),
            new PlanColorTheme("linear-gradient(to bottom right, #ffedd5 0%, #fed7aa 100%)", "#ea580c", "#c2410c", "#7c2d12")
    );

    private PlanColorTheme resolvePlanTheme(LearningPlan plan) {
        if (plan == null) {
            return PLAN_COLOR_THEMES.get(0);
        }
        if (planCombo != null && planCombo.getItems() != null) {
            for (int i = 0; i < planCombo.getItems().size(); i++) {
                LearningPlan item = planCombo.getItems().get(i);
                if (item != null && Objects.equals(item.getId(), plan.getId())) {
                    return PLAN_COLOR_THEMES.get(i % PLAN_COLOR_THEMES.size());
                }
            }
        }
        long id = plan.getId() != null ? Math.abs(plan.getId()) : 0L;
        return PLAN_COLOR_THEMES.get((int) (id % PLAN_COLOR_THEMES.size()));
    }

    private static String shortPlanBadgeText(LearningPlan plan) {
        if (plan == null || plan.getTitle() == null || plan.getTitle().isBlank()) {
            return "AI Plan";
        }
        String t = plan.getTitle().trim();
        return t.length() > 14 ? t.substring(0, 13) + "…" : t;
    }

    private VBox buildSlotCard(ScheduleSlot slot, long durationMinutes) {
        VBox card = new VBox(2);
        card.setUserData(slot);
        card.setMaxWidth(Double.MAX_VALUE);

        Label badgeLabel = new Label();
        badgeLabel.setMinWidth(0);
        HBox topRow;

        Button deleteBtn = new Button("✕");
        deleteBtn.setFocusTraversable(false);
        deleteBtn.setMinSize(18, 18);
        deleteBtn.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 9px; "
                        + "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 5; -fx-cursor: hand;");
        deleteBtn.setOnMouseClicked(ev -> ev.consume());
        deleteBtn.setOnAction(ev -> {
            ev.consume();
            if (slot.isPersonal() && slot.isRecurring()) {
                LocalDate occurrenceDate = resolveDateForCurrentWeek(slot);
                if (occurrenceDate == null) {
                    occurrenceDate = slot.getDate();
                }

                ButtonType btnOnlyThisWeek = new ButtonType(
                        "Chỉ tuần này (" + occurrenceDate.format(DateTimeFormatter.ofPattern("dd/MM")) + ")",
                        ButtonBar.ButtonData.YES);
                ButtonType btnAllWeeks = new ButtonType("Xóa cả chuỗi lặp", ButtonBar.ButtonData.NO);
                ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);

                Alert confirmAlert = new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Sự kiện '" + (slot.getTopic() != null ? slot.getTopic() : "")
                                + "' đang lặp hàng tuần.\nBạn muốn xóa chỉ trong tuần này ("
                                + occurrenceDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                + ") hay xóa toàn bộ chuỗi lặp?",
                        btnOnlyThisWeek, btnAllWeeks, btnCancel);
                confirmAlert.setTitle("Xóa sự kiện lặp lại");
                confirmAlert.setHeaderText("Xác nhận xóa sự kiện lặp tuần");
                prepareTimetableAlert(confirmAlert);

                Optional<ButtonType> choice = confirmAlert.showAndWait();
                if (choice.isEmpty() || choice.get() == btnCancel) {
                    return;
                }

                if (choice.get() == btnOnlyThisWeek) {
                    slot.addExcludedDate(occurrenceDate);
                    if (!slot.isDraft() && applicationContext != null) {
                        applicationContext.getScheduleSlotRepository().save(slot);
                    }
                    NotificationUtils.showSuccess("Đã xóa sự kiện khỏi tuần "
                            + occurrenceDate.format(DateTimeFormatter.ofPattern("dd/MM"))
                            + " (các tuần khác vẫn giữ nguyên).");
                    renderTimetable();
                    return;
                } else {
                    if (!slot.isDraft()) {
                        if (slot.getId() != null && applicationContext != null) {
                            applicationContext.getScheduleSlotRepository().delete(slot);
                        } else {
                            markDeletedIfPersisted(slot);
                        }
                    }
                    slots.remove(slot);
                    updateDraftBanner();
                    renderTimetable();
                    return;
                }
            }

            if (!slot.isDraft()) {
                if (slot.getId() != null && applicationContext != null) {
                    applicationContext.getScheduleSlotRepository().delete(slot);
                    if (slot.getPlan() != null && slot.getPlan().getId() != null) {
                        applicationContext.getLearningPlanService().calculateAndUpdateProgress(slot.getPlan().getId());
                    }
                } else {
                    markDeletedIfPersisted(slot);
                }
            }
            slots.remove(slot);
            updateDraftBanner();
            renderTimetable();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean isCompletedAi = slot.isAiPlan() && slot.isCompleted();
        PlanColorTheme planTheme = resolvePlanTheme(slot.getPlan());
        String planBadgeName = shortPlanBadgeText(slot.getPlan());

        if (slot.isPersonal()) {
            // LỊCH CÁ NHÂN / CỐ ĐỊNH: Tuyệt đối không có tính năng / ô đánh dấu hoàn thành
            card.getStyleClass().add("event-card-personal");
            badgeLabel.setText(slot.isRecurring() ? "📌 Cá nhân 🔁" : "📌 Cá nhân");
            badgeLabel.getStyleClass().add("personal-badge");
            badgeLabel.setStyle("-fx-font-size: 9.5px; -fx-padding: 1.5 5;");
            topRow = new HBox(4, badgeLabel, spacer, deleteBtn);
        } else {
            // LỊCH KẾ HOẠCH HỌC TẬP AI (AI_PLAN): Quản lý tiến độ học tập & đánh dấu hoàn thành
            CheckBox completeToggle = new CheckBox();
            completeToggle.setSelected(slot.isCompleted());
            completeToggle.setFocusTraversable(false);
            completeToggle.setStyle("-fx-font-size: 10px;");
            completeToggle.setTooltip(
                    new Tooltip(slot.isCompleted() ? "Đã hoàn thành (bấm để hủy)" : "Bấm để đánh dấu hoàn thành"));
            completeToggle.setOnMouseClicked(ev -> ev.consume());
            completeToggle.setOnAction(ev -> {
                ev.consume();
                slot.setCompleted(completeToggle.isSelected());
                if (!slot.isDraft() && applicationContext != null) {
                    applicationContext.getScheduleSlotRepository().save(slot);
                }
                if (slot.getPlan() != null && slot.getPlan().getId() != null && applicationContext != null) {
                    applicationContext.getLearningPlanService().calculateAndUpdateProgress(slot.getPlan().getId());
                }
                renderTimetable();
            });

            if (slot.isCompleted()) {
                card.getStyleClass().add("event-card-completed");
                badgeLabel.setText("✔ " + planBadgeName);
                badgeLabel.getStyleClass().add("completed-badge");
            } else if (slot.isDraft()) {
                card.getStyleClass().add("event-card-draft");
                badgeLabel.setText("⚡ " + planBadgeName);
                badgeLabel.getStyleClass().add("draft-badge");
                badgeLabel.setStyle("-fx-font-size: 9.5px; -fx-padding: 1.5 5;");
            } else {
                card.getStyleClass().add("event-card");
                card.setStyle("-fx-background-color: " + planTheme.bgGradient()
                        + "; -fx-border-color: " + planTheme.borderColor()
                        + "; -fx-border-width: 1.2 1.2 1.2 4; -fx-border-radius: 8; -fx-background-radius: 8;");
                badgeLabel.setText("🎓 " + planBadgeName);
                badgeLabel.setStyle("-fx-background-color: " + planTheme.badgeBg()
                        + "; -fx-text-fill: white; -fx-font-size: 9.5px; -fx-font-weight: bold; -fx-padding: 1.5 5; -fx-background-radius: 5;");
            }

            topRow = new HBox(4, completeToggle, badgeLabel, spacer, deleteBtn);
        }
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label topicLabel = new Label(slot.getTopic() != null ? slot.getTopic() : "");
        topicLabel.getStyleClass().add("event-topic");
        topicLabel.setWrapText(true);
        topicLabel.setMaxHeight(durationMinutes >= 90 ? 36 : 22);

        String subText = slot.getSubTopic() != null ? slot.getSubTopic().trim() : "";
        Label sub = null;
        if (!subText.isEmpty() && durationMinutes >= 75) {
            sub = new Label(subText);
            sub.getStyleClass().add("event-detail");
            sub.setWrapText(false);
            sub.setMaxHeight(16);
        }

        // Hiệu ứng hoàn thành (gạch ngang, đổi màu) CHỈ áp dụng cho lịch học AI
        if (isCompletedAi) {
            topicLabel.setStyle("-fx-strikethrough: true; -fx-text-fill: #15803d; -fx-font-weight: bold;");
            if (sub != null) {
                sub.setStyle("-fx-strikethrough: true; -fx-text-fill: #166534;");
            }
        } else if (slot.isAiPlan() && !slot.isDraft()) {
            topicLabel.setStyle("-fx-text-fill: " + planTheme.topicColor() + "; -fx-font-weight: bold;");
        }

        Region bottomPush = new Region();
        VBox.setVgrow(bottomPush, Priority.ALWAYS);

        Label time = new Label(slot.getStartTime() + " – " + slot.getEndTime());
        time.setStyle("-fx-font-size: 10px; -fx-font-weight: 600; -fx-text-fill: #475569;");

        Region metaSpacer = new Region();
        HBox.setHgrow(metaSpacer, Priority.ALWAYS);
        HBox bottomRow = new HBox(4, time, metaSpacer);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        if (slot.isReminderEnabled()) {
            Label reminderBadge = new Label("🔔" + slot.getReminderMinutes() + "p");
            reminderBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #c2410c; -fx-background-color: #ffedd5; -fx-padding: 0.5 4; -fx-background-radius: 4;");
            bottomRow.getChildren().add(reminderBadge);
        }

        if (sub != null) {
            card.getChildren().addAll(topRow, topicLabel, sub, bottomPush, bottomRow);
        } else {
            card.getChildren().addAll(topRow, topicLabel, bottomPush, bottomRow);
        }

        // Tooltip hiển thị đầy đủ thông tin khi di chuột vào ô trên TKB
        StringBuilder tipBuilder = new StringBuilder();
        if (slot.isPersonal()) {
            tipBuilder.append(slot.isRecurring() ? "📌 Lịch cá nhân (Lặp hàng tuần)\n" : "📌 Lịch cá nhân\n");
        } else {
            tipBuilder.append("🎓 Kế hoạch: ").append(planDisplayName(slot.getPlan())).append("\n");
        }
        tipBuilder.append("📖 Chủ đề: ").append(slot.getTopic() != null ? slot.getTopic() : "").append("\n");
        if (!subText.isEmpty()) {
            tipBuilder.append("🎯 Chi tiết: ").append(subText).append("\n");
        }
        tipBuilder.append("🕒 Thời gian: ").append(slot.getStartTime()).append(" – ").append(slot.getEndTime());
        if (slot.isReminderEnabled()) {
            tipBuilder.append("\n🔔 Nhắc qua Email: Trước ").append(slot.getReminderMinutes()).append(" phút");
        }
        Tooltip cardTooltip = new Tooltip(tipBuilder.toString());
        cardTooltip.setShowDelay(javafx.util.Duration.millis(250));
        Tooltip.install(card, cardTooltip);

        card.setOnMouseClicked(ev -> {
            if (ev.isStillSincePress()) {
                openSlotEditorDialog(slot, false);
            }
        });

        card.setOnDragDetected(ev -> {
            if (!ev.isPrimaryButtonDown()) {
                return;
            }
            draggingSlot = slot;
            draggingToken = UUID.randomUUID().toString();
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(draggingToken);
            db.setContent(cc);
            ev.consume();
        });
        card.setOnDragDone(ev -> {
            draggingSlot = null;
            draggingToken = null;
            ev.consume();
        });
        return card;
    }

    private void wireDropTarget(Label cell) {
        cell.setOnDragOver(ev -> {
            if (draggingSlot == null || draggingToken == null || !ev.getDragboard().hasString()) {
                return;
            }
            String token = ev.getDragboard().getString();
            if (!Objects.equals(token, draggingToken)) {
                return;
            }
            ev.acceptTransferModes(TransferMode.MOVE);
            DropCell dc = (DropCell) cell.getUserData();
            if (dc == null) {
                return;
            }
            ScheduleSlot moving = draggingSlot;
            LocalDate newDate = currentWeekStart.plusDays(dc.col() - 1L);
            LocalTime newStart = LocalTime.of(dc.hour(), moving.getStartTime().getMinute());
            LocalTime newEnd = newStart.plus(Duration.between(moving.getStartTime(), moving.getEndTime()));

            List<ScheduleSlot> overlaps = findOverlappingSlots(moving, newDate, newStart, newEnd);
            if (!overlaps.isEmpty()) {
                // Nếu là lịch cá nhân đè lên lịch AI thì hiển thị viền cam cảnh báo (cho phép
                // thả để gợi ý dời lịch AI)
                boolean onlyAiOverlaps = moving.isPersonal() && overlaps.stream().allMatch(ScheduleSlot::isAiPlan);
                if (onlyAiOverlaps) {
                    cell.setStyle(
                            "-fx-border-color: #f59e0b; -fx-border-width: 2; -fx-background-color: rgba(254,243,199,0.65);");
                } else {
                    cell.setStyle(
                            "-fx-border-color: #ef4444; -fx-border-width: 2; -fx-background-color: rgba(254,226,226,0.5);");
                }
            } else {
                cell.setStyle(
                        "-fx-border-color: #4ab5e2; -fx-border-width: 1.5; -fx-background-color: rgba(224,242,254,0.45);");
            }
            ev.consume();
        });

        cell.setOnDragExited(ev -> cell.setStyle(""));

        cell.setOnDragDropped(ev -> {
            cell.setStyle("");
            Dragboard db = ev.getDragboard();
            if (draggingSlot == null || draggingToken == null || !db.hasString()) {
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }
            if (!Objects.equals(db.getString(), draggingToken)) {
                ev.setDropCompleted(false);
                return;
            }
            DropCell dc = (DropCell) cell.getUserData();
            if (dc == null) {
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }
            ScheduleSlot moving = draggingSlot;
            LocalDate newDate = currentWeekStart.plusDays(dc.col() - 1L);
            LocalTime newStart = LocalTime.of(dc.hour(), moving.getStartTime().getMinute());
            LocalTime newEnd = newStart.plus(Duration.between(moving.getStartTime(), moving.getEndTime()));

            LocalDate currentEffectiveDate = resolveDateForCurrentWeek(moving);
            if (Objects.equals(currentEffectiveDate, newDate)
                    && Objects.equals(moving.getStartTime(), newStart)
                    && Objects.equals(moving.getEndTime(), newEnd)) {
                ev.setDropCompleted(true);
                ev.consume();
                return;
            }

            List<ScheduleSlot> overlaps = findOverlappingSlots(moving, newDate, newStart, newEnd);
            if (!overlaps.isEmpty()) {
                boolean canResolveAi = moving.isPersonal() && overlaps.stream().allMatch(ScheduleSlot::isAiPlan);
                if (canResolveAi) {
                    // Tránh gọi showAndWait trực tiếp bên trong sự kiện DragDropped của JavaFX
                    final List<ScheduleSlot> conflictedAi = new ArrayList<>(overlaps);
                    Platform.runLater(() -> {
                        if (resolvePersonalOverAiConflict(conflictedAi, newDate, newStart, newEnd)) {
                            if (moving.isPersonal() && moving.isRecurring()) {
                                long diffDays = newDate.getDayOfWeek().getValue() - moving.getDate().getDayOfWeek().getValue();
                                moving.setDate(moving.getDate().plusDays(diffDays));
                                moving.setStartTime(newStart);
                                moving.setEndTime(newEnd);
                            } else {
                                moving.setDate(newDate);
                                moving.setStartTime(newStart);
                                moving.setEndTime(newEnd);
                            }
                            renderTimetable();
                        }
                    });
                    ev.setDropCompleted(true);
                    ev.consume();
                    draggingSlot = null;
                    draggingToken = null;
                    return;
                }

                alert(Alert.AlertType.WARNING, "Trùng lịch",
                        "Không thể thả vào ô này vì trùng thời gian với sự kiện khác.");
                ev.setDropCompleted(false);
                ev.consume();
                return;
            }

            if (moving.isPersonal() && moving.isRecurring()) {
                long diffDays = newDate.getDayOfWeek().getValue() - moving.getDate().getDayOfWeek().getValue();
                moving.setDate(moving.getDate().plusDays(diffDays));
                moving.setStartTime(newStart);
                moving.setEndTime(newEnd);
            } else {
                moving.setDate(newDate);
                moving.setStartTime(newStart);
                moving.setEndTime(newEnd);
            }
            ev.setDropCompleted(true);
            ev.consume();
            draggingSlot = null;
            draggingToken = null;
            renderTimetable();
        });
    }

    /**
     * Tìm tất cả các slot trên lưới tuần hiện tại bị giao thời gian với khoảng
     * (date, start, end).
     */
    private List<ScheduleSlot> findOverlappingSlots(ScheduleSlot self, LocalDate targetDate, LocalTime start, LocalTime end) {
        List<ScheduleSlot> result = new ArrayList<>();
        for (ScheduleSlot o : slots) {
            if (o == self) {
                continue;
            }
            if (o.getDate() == null || o.getStartTime() == null || o.getEndTime() == null) {
                continue;
            }
            boolean sameDay = false;
            if (Objects.equals(o.getDate(), targetDate)) {
                if (!o.isDateExcluded(targetDate)) {
                    sameDay = true;
                }
            } else if (o.isPersonal() && o.isRecurring()) {
                if (o.getDate().getDayOfWeek() == targetDate.getDayOfWeek()) {
                    if (!targetDate.isBefore(o.getDate())
                            && (o.getRecurringEndDate() == null || !targetDate.isAfter(o.getRecurringEndDate()))) {
                        if (!o.isDateExcluded(targetDate)) {
                            sameDay = true;
                        }
                    }
                }
            }
            if (!sameDay) {
                continue;
            }
            if (start.isBefore(o.getEndTime()) && o.getStartTime().isBefore(end)) {
                result.add(o);
            }
        }
        return result;
    }

    private void openExternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop API không được hỗ trợ trên hệ thống này.");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Không hỗ trợ mở trình duyệt mặc định.");
            }
            desktop.browse(URI.create(url));
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Mở liên kết thất bại",
                    "Không thể mở đường dẫn trên trình duyệt: " + e.getMessage());
        }
    }

    private void setBusy(boolean busy) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(busy);
            loadingIndicator.setManaged(busy);
            btnGenerateAi.setDisable(busy);
            btnSaveDb.setDisable(busy);
            btnAddManual.setDisable(busy);
            planCombo.setDisable(busy);
            if (btnDiscardDraft != null) {
                btnDiscardDraft.setDisable(busy);
            }
            if (btnApplyDraft != null) {
                btnApplyDraft.setDisable(busy);
            }
        });
    }

    private void alert(Alert.AlertType type, String title, String message) {
        if (Platform.isFxApplicationThread()) {
            doAlert(type, title, message);
        } else {
            Platform.runLater(() -> doAlert(type, title, message));
        }
    }

    private static void ensureDialogStylesheet(DialogPane pane) {
        if (!pane.getStylesheets().contains(TIMETABLE_DIALOG_STYLESHEET)) {
            pane.getStylesheets().add(TIMETABLE_DIALOG_STYLESHEET);
        }
    }

    private static void playDialogEntrance(DialogPane pane) {
        pane.setOpacity(0);
        pane.setScaleX(0.92);
        pane.setScaleY(0.92);
        FadeTransition fade = new FadeTransition(javafx.util.Duration.millis(260), pane);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(javafx.util.Duration.millis(260), pane);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1.0);
        scale.setToY(1.0);
        new ParallelTransition(fade, scale).play();
    }

    private static void prepareTimetableDialog(Dialog<?> dialog, String variant) {
        DialogPane pane = dialog.getDialogPane();
        ensureDialogStylesheet(pane);
        pane.getStyleClass().removeIf(c -> c.startsWith("timetable-dialog--"));
        pane.getStyleClass().add("timetable-dialog");
        pane.getStyleClass().add("timetable-dialog--" + variant);
        Window.getWindows().stream()
                .filter(w -> w != null && w.isShowing())
                .findFirst()
                .ifPresent(dialog::initOwner);

        for (ButtonType bt : pane.getButtonTypes()) {
            javafx.scene.Node btnNode = pane.lookupButton(bt);
            if (btnNode instanceof Button btn) {
                ButtonBar.setButtonUniformSize(btn, false);
                btn.setMinWidth(Region.USE_PREF_SIZE);
                if (bt.getButtonData() == ButtonBar.ButtonData.NO) {
                    btn.setStyle("-fx-background-color: #fef2f2; -fx-text-fill: #dc2626; -fx-border-color: #fca5a5; -fx-border-width: 1; -fx-border-radius: 10; -fx-background-radius: 10;");
                }
            }
        }

        dialog.setOnShown(e -> {
            for (ButtonType bt : pane.getButtonTypes()) {
                javafx.scene.Node btnNode = pane.lookupButton(bt);
                if (btnNode instanceof Button btn) {
                    ButtonBar.setButtonUniformSize(btn, false);
                    btn.setMinWidth(Region.USE_PREF_SIZE);
                }
            }
            playDialogEntrance(pane);
        });
    }

    private static void prepareTimetableAlert(Alert alert) {
        prepareTimetableDialog(alert, alert.getAlertType().name().toLowerCase(Locale.ROOT));
    }

    private static void doAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        prepareTimetableAlert(alert);

        if (type == Alert.AlertType.INFORMATION) {
            alert.initModality(Modality.NONE);
            alert.show();
            return;
        }

        alert.showAndWait();
    }

    private record DropCell(int col, int hour) {
    }

    private static final class LearningPlanListCell extends ListCell<LearningPlan> {
        @Override
        protected void updateItem(LearningPlan item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                String t = item.getTitle();
                if (t != null && !t.isBlank()) {
                    setText(t);
                } else {
                    setText("Plan #" + item.getId());
                }
            }
        }
    }
}
