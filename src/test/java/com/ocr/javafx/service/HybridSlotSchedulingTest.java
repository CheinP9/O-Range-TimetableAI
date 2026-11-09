package com.ocr.javafx.service;

import com.ocr.javafx.dto.SlotCandidate;
import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.entity.ScheduleSlot;
import com.ocr.javafx.entity.User;
import com.ocr.javafx.enums.ScheduleStatus;
import com.ocr.javafx.enums.ScheduleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HybridSlotSchedulingTest {

    @Test
    void testExtractFreeSlotsRespectsBusyAndRecurringPersonalSlots() {
        SlotExtractionService extractor = new SlotExtractionService();
        LocalDate weekStart = LocalDate.of(2026, 11, 9); // Monday
        LocalDate weekEnd = weekStart.plusDays(6);

        // Lịch cá nhân lặp lại từ tuần trước vào Thứ Hai 08:00 - 11:30
        ScheduleSlot recurringSchool = new ScheduleSlot();
        recurringSchool.setDate(LocalDate.of(2026, 11, 2)); // Previous Monday
        recurringSchool.setStartTime(LocalTime.of(8, 0));
        recurringSchool.setEndTime(LocalTime.of(11, 30));
        recurringSchool.setScheduleType(ScheduleType.PERSONAL);
        recurringSchool.setRecurring(true);

        List<SlotCandidate> freeSlots = extractor.extractFreeSlots(
                weekStart, weekEnd, List.of(recurringSchool), 90
        );

        assertFalse(freeSlots.isEmpty(), "Phải tìm thấy các slot trống trong tuần");

        // Không có slot nào rơi vào khoảng 08:00 - 11:30 của Thứ Hai (2026-11-09)
        boolean anyOverlapsRecurring = freeSlots.stream()
                .filter(c -> c.date().equals(weekStart))
                .anyMatch(c -> c.startTime().isBefore(LocalTime.of(11, 30))
                        && LocalTime.of(8, 0).isBefore(c.endTime()));

        assertFalse(anyOverlapsRecurring, "Không được sinh slot trùng vào lịch cá nhân lặp tuần");
        assertEquals("SLOT_1", freeSlots.get(0).slotId());
    }

    @Test
    void testFindNearestFreeSlotForConflictResolution() {
        SlotExtractionService extractor = new SlotExtractionService();
        LocalDate weekStart = LocalDate.of(2026, 11, 9);
        LocalDate weekEnd = weekStart.plusDays(6);

        ScheduleSlot personalConflict = new ScheduleSlot();
        personalConflict.setDate(weekStart);
        personalConflict.setStartTime(LocalTime.of(8, 0));
        personalConflict.setEndTime(LocalTime.of(10, 0));
        personalConflict.setScheduleType(ScheduleType.PERSONAL);

        Optional<SlotCandidate> nearest = extractor.findNearestFreeSlot(
                weekStart,
                LocalTime.of(8, 0),
                90,
                weekStart,
                weekEnd,
                List.of(personalConflict)
        );

        assertTrue(nearest.isPresent());
        SlotCandidate relocated = nearest.get();
        assertEquals(weekStart, relocated.date());
        assertFalse(
                relocated.startTime().isBefore(LocalTime.of(10, 0)) && LocalTime.of(8, 0).isBefore(relocated.endTime()),
                "Slot dời sang không được trùng với lịch cá nhân vừa chèn"
        );
    }

    @Test
    void testPedagogicalPromptAndParseDraftSlots() throws Exception {
        AISchedulingService aiService = new AISchedulingService();
        SlotExtractionService extractor = new SlotExtractionService();

        LocalDate weekStart = LocalDate.of(2026, 11, 9);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<SlotCandidate> candidates = extractor.extractFreeSlots(weekStart, weekEnd, List.of(), 90);

        LearningPlan plan = new LearningPlan();
        plan.setId(10L);
        plan.setTitle("Java Backend Mastery");
        plan.setDomain("Java");
        plan.setGoal("Thành thạo Core Java và OOP");

        ScheduleSlot week1Slot = new ScheduleSlot();
        week1Slot.setDate(LocalDate.of(2026, 11, 2));
        week1Slot.setTopic("Cú pháp Java cơ bản");
        week1Slot.setSubTopic("Biến, kiểu dữ liệu, toán tử");
        week1Slot.setCompleted(true);

        String prompt = aiService.buildPedagogicalPrompt(plan, candidates, 2, List.of(week1Slot));
        assertTrue(prompt.contains("Cú pháp Java cơ bản"), "Prompt phải chứa lịch sử bài đã học ở tuần trước");
        assertTrue(prompt.contains("SLOT_1"), "Prompt phải chứa danh sách SLOT_ID");

        String mockAiJson = """
                [
                  {
                    "slot_id": "SLOT_1",
                    "topic": "Lập trình hướng đối tượng (OOP)",
                    "sub_goals": "Class, Object, Encapsulation",
                    "notes": "https://docs.oracle.com/javase/tutorial/java/concepts/"
                  },
                  {
                    "slot_id": "SLOT_3",
                    "topic": "Kế thừa và Đa hình",
                    "sub_goals": "Inheritance, Polymorphism, Overriding",
                    "notes": "Thực hành bài tập kế thừa"
                  }
                ]
                """;

        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");

        List<ScheduleSlot> drafts = aiService.parseAiResponseToDraftSlots(mockAiJson, candidates, 2, plan, user);
        assertEquals(2, drafts.size());

        ScheduleSlot first = drafts.get(0);
        assertEquals(ScheduleStatus.DRAFT, first.getStatus());
        assertEquals(ScheduleType.AI_PLAN, first.getScheduleType());
        assertEquals(candidates.get(0).date(), first.getDate());
        assertEquals(candidates.get(0).startTime(), first.getStartTime());
        assertEquals(candidates.get(0).endTime(), first.getEndTime());
        assertEquals("Lập trình hướng đối tượng (OOP)", first.getTopic());
    }

    @Test
    void testRecurringSlotPastAndEndDateBounds() {
        SlotExtractionService extractor = new SlotExtractionService();
        LocalDate weekStart = LocalDate.of(2026, 11, 9); // Monday
        LocalDate weekEnd = weekStart.plusDays(6);

        // 1. Recurring slot created in FUTURE (starts 2026-11-16): should NOT block current week 2026-11-09
        ScheduleSlot futureRecurring = new ScheduleSlot();
        futureRecurring.setDate(LocalDate.of(2026, 11, 16));
        futureRecurring.setStartTime(LocalTime.of(8, 0));
        futureRecurring.setEndTime(LocalTime.of(10, 0));
        futureRecurring.setScheduleType(ScheduleType.PERSONAL);
        futureRecurring.setRecurring(true);

        List<SlotCandidate> freeSlots1 = extractor.extractFreeSlots(weekStart, weekEnd, List.of(futureRecurring), 90);
        boolean monday8Blocked = freeSlots1.stream()
                .filter(c -> c.date().equals(weekStart))
                .noneMatch(c -> c.startTime().equals(LocalTime.of(8, 0)));
        assertFalse(monday8Blocked, "Slot bắt đầu trong tương lai không được chặn tuần hiện tại");

        // 2. Recurring slot with recurringEndDate in PAST (ended 2026-11-05): should NOT block current week 2026-11-09
        ScheduleSlot endedRecurring = new ScheduleSlot();
        endedRecurring.setDate(LocalDate.of(2026, 10, 1));
        endedRecurring.setRecurringEndDate(LocalDate.of(2026, 11, 5));
        endedRecurring.setStartTime(LocalTime.of(8, 0));
        endedRecurring.setEndTime(LocalTime.of(10, 0));
        endedRecurring.setScheduleType(ScheduleType.PERSONAL);
        endedRecurring.setRecurring(true);

        List<SlotCandidate> freeSlots2 = extractor.extractFreeSlots(weekStart, weekEnd, List.of(endedRecurring), 90);
        boolean monday8EndedBlocked = freeSlots2.stream()
                .filter(c -> c.date().equals(weekStart))
                .noneMatch(c -> c.startTime().equals(LocalTime.of(8, 0)));
        assertFalse(monday8EndedBlocked, "Slot đã kết thúc trước tuần hiện tại không được chặn tuần này");
    }

    @Test
    void testMidWeekExcludesPastDaysAndPastHours() {
        SlotExtractionService extractor = new SlotExtractionService();
        LocalDate weekStart = LocalDate.of(2026, 11, 2); // Monday
        LocalDate weekEnd = LocalDate.of(2026, 11, 8);   // Sunday

        // Giả lập người dùng nhấn tạo lịch vào Thứ 5 lúc 14:00 chiều
        LocalDateTime simulatedNow = LocalDateTime.of(2026, 11, 5, 14, 0);

        List<SlotCandidate> freeSlots = extractor.extractFreeSlots(
                weekStart, weekEnd, List.of(), 90, simulatedNow
        );

        assertFalse(freeSlots.isEmpty(), "Vẫn phải có các slot trống cho nửa cuối tuần (Thứ 5 chiều -> Chủ nhật)");

        // 1. Tuyệt đối không có bất kỳ slot nào rơi vào Thứ 2, Thứ 3, Thứ 4 (quá khứ)
        boolean hasPastDays = freeSlots.stream()
                .anyMatch(c -> c.date().isBefore(simulatedNow.toLocalDate()));
        assertFalse(hasPastDays, "Không được xếp lịch vào các ngày trước Thứ 5");

        // 2. Vào Thứ 5 (hôm nay), tuyệt đối không có slot nào bắt đầu trước 14:00 (giờ sáng đã qua)
        boolean hasPastHoursToday = freeSlots.stream()
                .filter(c -> c.date().equals(simulatedNow.toLocalDate()))
                .anyMatch(c -> c.startTime().isBefore(simulatedNow.toLocalTime()));
        assertFalse(hasPastHoursToday, "Không được xếp lịch vào các giờ trước 14:00 của ngày hôm nay");

        // 3. Đối với tuần tương lai (2026-11-09 -> 2026-11-15): Cho phép xếp từ Thứ 2 sáng 06:00 như bình thường
        LocalDate futureWeekStart = LocalDate.of(2026, 11, 9);
        LocalDate futureWeekEnd = futureWeekStart.plusDays(6);
        List<SlotCandidate> futureSlots = extractor.extractFreeSlots(
                futureWeekStart, futureWeekEnd, List.of(), 90, simulatedNow
        );
        boolean mondayMorningExists = futureSlots.stream()
                .anyMatch(c -> c.date().equals(futureWeekStart) && c.startTime().equals(LocalTime.of(6, 0)));
        assertTrue(mondayMorningExists, "Tuần tương lai phải cho phép xếp từ đầu tuần bình thường");
    }

    public static void main(String[] args) throws Exception {
        HybridSlotSchedulingTest test = new HybridSlotSchedulingTest();
        test.testExtractFreeSlotsRespectsBusyAndRecurringPersonalSlots();
        System.out.println("[PASS] testExtractFreeSlotsRespectsBusyAndRecurringPersonalSlots");
        test.testFindNearestFreeSlotForConflictResolution();
        System.out.println("[PASS] testFindNearestFreeSlotForConflictResolution");
        test.testPedagogicalPromptAndParseDraftSlots();
        System.out.println("[PASS] testPedagogicalPromptAndParseDraftSlots");
        test.testRecurringSlotPastAndEndDateBounds();
        System.out.println("[PASS] testRecurringSlotPastAndEndDateBounds");
        test.testMidWeekExcludesPastDaysAndPastHours();
        System.out.println("[PASS] testMidWeekExcludesPastDaysAndPastHours");
        System.out.println("ALL HYBRID SLOT SCHEDULING TESTS PASSED SUCCESSFULLY!");
    }
}

