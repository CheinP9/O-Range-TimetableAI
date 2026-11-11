package com.ocr.javafx.controller.views;

import com.ocr.javafx.ApplicationContext;
import com.ocr.javafx.controller.components.*;
import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.entity.ScheduleSlot;
import com.ocr.javafx.enums.LearningPlanStatus;
import com.ocr.javafx.util.HibernateUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.hibernate.Session;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DashboardController {
    @FXML
    private VBox activePlans;

    @FXML
    public AnchorPane barWrapper;

    @FXML
    public AnchorPane pieWrapper;

    @FXML
    public HBox rootHBox;

    @FXML
    private BarchartController barchartController;

    @FXML
    private StatsRowController statsRowController;

    @FXML
    private PiechartController piechartController;

    @FXML
    private ActivePlansController activePlansController;

    @FXML
    public VBox barchart;

    public void init(ApplicationContext applicationContext) {
        statsRowController.setApplicationContext(applicationContext);
        barchartController.setApplicationContext(applicationContext);
        barchartController.initChartAxes();
        piechartController.setApplicationContext(applicationContext);
        piechartController.initChartLayout();
        activePlansController.setApplicationContext(applicationContext);

        barWrapper.prefWidthProperty().bind(
                rootHBox.widthProperty().multiply(0.66)
        );
        pieWrapper.prefWidthProperty().bind(
                rootHBox.widthProperty().multiply(0.34)
        );

        Long userId = applicationContext.getSessionManager().getCurrentUser() != null
                ? applicationContext.getSessionManager().getCurrentUser().getId()
                : null;
        if (userId == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Chỉ thực hiện 2 truy vấn trong cùng 1 phiên làm việc thay vì hơn 20 truy vấn tuần tự trên luồng UI
                List<ScheduleSlot> sessions = applicationContext.getScheduleSlotRepository().findByUserId(userId);
                List<LearningPlan> rawPlans;
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    rawPlans = applicationContext.getLearningPlanRepository().findByUserId(session, userId);
                }

                // Tính số buổi học theo từng plan ngay trên bộ nhớ từ danh sách sessions đã tải
                Map<Long, long[]> countsByPlan = new HashMap<>();
                double totalMinutes = 0;
                Map<DayOfWeek, Double> weeklyHours = new HashMap<>();
                Map<String, Double> skillDistribution = new HashMap<>();

                for (ScheduleSlot s : sessions) {
                    if (!s.isLearningPlanStudy()) {
                        continue;
                    }
                    long mins = (s.getStartTime() != null && s.getEndTime() != null)
                            ? Duration.between(s.getStartTime(), s.getEndTime()).toMinutes()
                            : 0;
                    double hrs = mins / 60.0;

                    if (s.getPlan() != null && s.getPlan().getId() != null) {
                        long[] c = countsByPlan.computeIfAbsent(s.getPlan().getId(), k -> new long[]{ 0L, 0L });
                        c[0]++;
                        if (s.isCompleted()) {
                            c[1]++;
                        }
                        String planTitle = s.getPlan().getTitle();
                        String shortName = planTitle == null ? "" : (planTitle.length() > 15 ? planTitle.substring(0, 15) + "..." : planTitle);
                        skillDistribution.put(shortName, skillDistribution.getOrDefault(shortName, 0.0) + hrs);
                    }

                    if (s.isCompleted()) {
                        totalMinutes += mins;
                        if (s.getDate() != null) {
                            DayOfWeek day = s.getDate().getDayOfWeek();
                            weeklyHours.put(day, weeklyHours.getOrDefault(day, 0.0) + hrs);
                        }
                    }
                }

                long completedCount = 0;
                long inProgressCount = 0;
                long notStartedCount = 0;
                List<LearningPlan> activeList = new ArrayList<>();

                for (LearningPlan plan : rawPlans) {
                    long[] counts = countsByPlan.getOrDefault(plan.getId(), new long[]{ 0L, 0L });
                    long genTotal = counts[0];
                    long comp = counts[1];
                    int req = plan.getEffectiveTargetSessions();
                    long denom = req > 0 ? req : genTotal;
                    int prog = denom > 0 ? (int) Math.min(100, Math.round(((double) comp / denom) * 100)) : 0;

                    LearningPlanStatus status;
                    if (comp == 0 && genTotal == 0) {
                        status = LearningPlanStatus.NOT_STARTED;
                        notStartedCount++;
                    } else if (prog < 100) {
                        status = LearningPlanStatus.IN_PROGRESS;
                        inProgressCount++;
                    } else {
                        status = LearningPlanStatus.COMPLETED;
                        completedCount++;
                    }

                    plan.setProgress(prog);
                    plan.setStatus(status);
                    if (status == LearningPlanStatus.IN_PROGRESS) {
                        activeList.add(plan);
                    }
                }

                long totalPlans = rawPlans.size();
                long startedPlans = totalPlans - notStartedCount;
                double overallProgress = startedPlans == 0 ? 0.0 : (completedCount * 100.0) / startedPlans;
                double totalHours = totalMinutes / 60.0;

                final long finalCompleted = completedCount;
                final long finalInProgress = inProgressCount;

                Platform.runLater(() -> {
                    statsRowController.applyStats(totalHours, finalCompleted, finalInProgress, overallProgress);
                    barchartController.applyData(weeklyHours);
                    piechartController.applyData(skillDistribution);
                    activePlansController.applyActivePlans(activeList);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
