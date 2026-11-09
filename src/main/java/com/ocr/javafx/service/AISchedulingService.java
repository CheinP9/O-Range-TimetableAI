package com.ocr.javafx.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocr.javafx.dto.SlotCandidate;
import com.ocr.javafx.entity.LearningPlan;
import com.ocr.javafx.entity.ScheduleSlot;
import com.ocr.javafx.entity.User;
import com.ocr.javafx.enums.ScheduleStatus;
import com.ocr.javafx.enums.ScheduleType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AISchedulingService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiSlotSelection {
        @JsonProperty("slot_id")
        @JsonAlias({"slotId", "id"})
        public String slotId;

        @JsonProperty("topic")
        public String topic;

        @JsonProperty("sub_goals")
        @JsonAlias({"subGoals", "subTopic", "sub_topic"})
        public String subGoals;

        @JsonProperty("notes")
        @JsonAlias({"resourceUrl", "resource_url", "note"})
        public String notes;
    }

    public String buildPedagogicalPrompt(
            LearningPlan plan,
            List<SlotCandidate> candidates,
            int requestedSessions,
            List<ScheduleSlot> previousWeeksSlots
    ) {
        String title = plan.getTitle() != null && !plan.getTitle().isBlank() ? plan.getTitle().trim() : "Kế hoạch học tập";
        String domain = plan.getDomain() != null && !plan.getDomain().isBlank() ? plan.getDomain().trim() : "Tổng hợp";
        String goal = plan.getGoal() != null && !plan.getGoal().isBlank() ? plan.getGoal().trim() : title;
        String intensity = plan.getIntensity() != null && !plan.getIntensity().isBlank() ? plan.getIntensity().trim() : "Medium";
        String skills = (plan.getSkills() != null && !plan.getSkills().isEmpty())
                ? String.join(", ", plan.getSkills())
                : "(Tự động phân bổ theo mục tiêu)";

        StringBuilder historyBuilder = new StringBuilder();
        if (previousWeeksSlots == null || previousWeeksSlots.isEmpty()) {
            historyBuilder.append("(Đây là tuần học đầu tiên của kế hoạch, hãy bắt đầu từ các kiến thức nền tảng nhất)");
        } else {
            int idx = 1;
            for (ScheduleSlot prev : previousWeeksSlots) {
                if (prev.getTopic() == null || prev.getTopic().isBlank()) {
                    continue;
                }
                historyBuilder.append("- Buổi ").append(idx++).append(" (").append(prev.getDate()).append("): ")
                        .append(prev.getTopic().trim());
                if (prev.getSubTopic() != null && !prev.getSubTopic().isBlank()) {
                    historyBuilder.append(" — ").append(prev.getSubTopic().trim());
                }
                if (prev.isCompleted()) {
                    historyBuilder.append(" [Đã hoàn thành]");
                }
                historyBuilder.append("\n");
            }
            if (historyBuilder.isEmpty()) {
                historyBuilder.append("(Đây là tuần học đầu tiên của kế hoạch, hãy bắt đầu từ các kiến thức nền tảng nhất)");
            }
        }

        String candidatesText = candidates.stream()
                .map(c -> "- " + c.slotId() + ": " + c.displayText())
                .collect(Collectors.joining("\n"));

        return "Bạn là một chuyên gia sư phạm và lập lịch học tập thông minh.\n"
                + "Nhiệm vụ của bạn là chọn đúng số lượng buổi học từ danh sách khung giờ trống được cấp và xây dựng nội dung bài học NỐI TIẾP lộ trình học tập của người dùng.\n\n"
                + "----------------------------------------------------------------------\n"
                + "THÔNG TIN KẾ HOẠCH HỌC TẬP (LEARNING PLAN):\n"
                + "- Tên kế hoạch: " + title + "\n"
                + "- Lĩnh vực / Môn học: " + domain + "\n"
                + "- Mục tiêu dài hạn: " + goal + "\n"
                + "- Cường độ / Mức độ: " + intensity + "\n"
                + "- Kỹ năng trọng tâm: " + skills + "\n\n"
                + "LỊCH SỬ CÁC BÀI ĐÃ HỌC Ở CÁC TUẦN TRƯỚC:\n"
                + historyBuilder.toString().trim() + "\n\n"
                + "DANH SÁCH KHUNG GIỜ TRỐNG ĐÃ ĐƯỢC HỆ THỐNG XÁC THỰC CỦA TUẦN NÀY (06:00 - 23:00):\n"
                + candidatesText + "\n"
                + "----------------------------------------------------------------------\n"
                + "YÊU CẦU BẮT BUỘC DÀNH CHO BẠN:\n"
                + "1. Bạn phải chọn ĐÚNG CHÍNH XÁC N = " + requestedSessions + " slot khác nhau từ danh sách SLOT_ID được cấp ở trên.\n"
                + "   - Ưu tiên phân bổ đều các ngày trong tuần để người dùng có thời gian tiếp thu và thực hành.\n"
                + "   - TUYỆT ĐỐI KHÔNG tự bịa ra ngày hoặc giờ mới ngoài danh sách SLOT_ID đã cho.\n"
                + "2. Xây dựng nội dung bài học NỐI TIẾP các bài đã học ở các tuần trước theo mức độ tăng dần.\n"
                + "   - TUYỆT ĐỐI KHÔNG lặp lại những chủ đề người dùng đã học ở phần lịch sử.\n"
                + "3. Định dạng phản hồi: Chỉ trả về DUY NHẤT một mảng JSON thuần túy (không giải thích, không markdown code fence):\n"
                + "[\n"
                + "  {\n"
                + "    \"slot_id\": \"SLOT_X\",\n"
                + "    \"topic\": \"Tên chủ đề bài học cụ thể\",\n"
                + "    \"sub_goals\": \"Mục tiêu chi tiết cần nắm vững trong buổi học\",\n"
                + "    \"notes\": \"Ghi chú hướng dẫn học tập hoặc link tài liệu uy tín (không dùng YouTube)\"\n"
                + "  }\n"
                + "]\n";
    }

    public List<ScheduleSlot> generateDraftSchedule(
            LearningPlan plan,
            User user,
            List<SlotCandidate> candidates,
            int requestedSessions,
            List<ScheduleSlot> previousWeeksSlots
    ) throws Exception {
        String prompt = buildPedagogicalPrompt(plan, candidates, requestedSessions, previousWeeksSlots);
        String rawResponse = OpenRouterAI.ask(prompt);
        return parseAiResponseToDraftSlots(rawResponse, candidates, requestedSessions, plan, user);
    }

    public List<ScheduleSlot> parseAiResponseToDraftSlots(
            String raw,
            List<SlotCandidate> candidates,
            int requestedSessions,
            LearningPlan plan,
            User user
    ) throws JsonProcessingException {
        if (raw != null && raw.trim().startsWith("API Error:")) {
            throw new IllegalStateException(raw.trim());
        }

        String json = extractJsonArrayPayload(raw);
        List<AiSlotSelection> selections = objectMapper.readValue(json, new TypeReference<List<AiSlotSelection>>() {});

        Map<String, SlotCandidate> candidateMap = new LinkedHashMap<>();
        for (SlotCandidate c : candidates) {
            candidateMap.put(c.slotId().toUpperCase(Locale.ROOT), c);
        }

        Set<String> usedSlotIds = new HashSet<>();
        List<ScheduleSlot> draftSlots = new ArrayList<>();

        for (AiSlotSelection sel : selections) {
            if (draftSlots.size() >= requestedSessions) {
                break;
            }
            if (sel == null || sel.topic == null || sel.topic.isBlank()) {
                continue;
            }

            SlotCandidate matched = null;
            if (sel.slotId != null) {
                String normalizedId = sel.slotId.trim().toUpperCase(Locale.ROOT);
                if (!usedSlotIds.contains(normalizedId)) {
                    matched = candidateMap.get(normalizedId);
                }
            }

            // Fallback an toàn nếu AI trả về slot_id lệch định dạng hoặc trùng
            if (matched == null) {
                for (SlotCandidate c : candidates) {
                    String id = c.slotId().toUpperCase(Locale.ROOT);
                    if (!usedSlotIds.contains(id)) {
                        matched = c;
                        break;
                    }
                }
            }

            if (matched == null) {
                break;
            }

            usedSlotIds.add(matched.slotId().toUpperCase(Locale.ROOT));

            ScheduleSlot slot = new ScheduleSlot();
            slot.setDate(matched.date());
            slot.setStartTime(matched.startTime());
            slot.setEndTime(matched.endTime());
            slot.setTopic(safeTruncate(sel.topic, 255));
            slot.setSubTopic(safeTruncate(sel.subGoals, 255));

            String notes = sel.notes != null ? sel.notes.trim() : "";
            if (isYouTubeUrl(notes)) {
                notes = "";
            }
            slot.setResourceUrl(safeTruncate(notes, 255));

            slot.setUser(user);
            slot.setPlan(plan);
            slot.setScheduleType(ScheduleType.AI_PLAN);
            slot.setStatus(ScheduleStatus.DRAFT);
            slot.setRecurring(false);
            slot.setCompleted(false);
            slot.setReminderEnabled(true);
            slot.setReminderMinutes(15);
            slot.setReminderMessage("Nhắc nhở buổi học AI: " + slot.getTopic() + " vào lúc " + slot.getStartTime());

            draftSlots.add(slot);
        }

        if (draftSlots.isEmpty()) {
            throw new IllegalStateException("AI không trả về buổi học hợp lệ nào từ danh sách slot trống.");
        }

        return draftSlots;
    }

    private static String extractJsonArrayPayload(String raw) {
        if (raw == null) {
            return "[]";
        }
        String t = raw.trim();
        int fence = t.indexOf("```");
        if (fence >= 0) {
            int lineAfter = t.indexOf('\n', fence);
            int start = lineAfter >= 0 ? lineAfter + 1 : fence + 3;
            int endFence = t.indexOf("```", start);
            if (endFence > start) {
                t = t.substring(start, endFence).trim();
            }
        }
        int lb = t.indexOf('[');
        int rb = t.lastIndexOf(']');
        if (lb >= 0 && rb > lb) {
            return t.substring(lb, rb + 1);
        }
        return t;
    }

    private static boolean isYouTubeUrl(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return u.contains("youtube.com") || u.contains("youtu.be");
    }

    private static String safeTruncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (maxLength > 0 && trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }
}

