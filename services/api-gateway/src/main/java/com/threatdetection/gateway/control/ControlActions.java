package com.threatdetection.gateway.control;

import java.util.Map;
import java.util.Set;

final class ControlActions {
    static final Set<String> ALL = Set.of(
            "capabilities", "device.status", "config.read", "config.history", "config.validate",
            "config.draft", "config.push", "config.restore", "lock.read", "lock.set", "lock.clear",
            "candidates.pull", "candidates.approve", "commands.history", "task.get",
            "channel.diagnostics", "policy.list", "violation.list", "cmd.send", "task.retry",
            "task.cancel", "channel.reconcile.start", "channel.reconcile.review", "channel.repair",
            "policy.create", "policy.update", "policy.delete", "violation.acknowledge");
    static final Set<String> READ = Set.of(
            "capabilities", "device.status", "config.read", "config.history", "config.validate",
            "lock.read", "commands.history", "task.get", "channel.diagnostics", "policy.list", "violation.list");

    private ControlActions() {}

    static String validationError(String action, Map<String, Object> params) {
        if (action == null || !ALL.contains(action)) return "Unsupported control action";
        if (("candidates.approve".equals(action) || "channel.reconcile.review".equals(action))
                && !Boolean.TRUE.equals(params.get("human_confirmed"))) return "Human confirmation is required";
        if ("cmd.send".equals(action)) {
            if (containsCommand(params, "containment_approve")) return "Use candidates.approve for containment approval";
            if (containsCommand(params, "containment_candidates")) return "Use candidates.pull for containment candidates";
        }
        return null;
    }

    private static boolean containsCommand(Object value, String blocked) {
        if (value instanceof String command) return blocked.equalsIgnoreCase(command.strip());
        if (value instanceof Map<?, ?> object) return object.values().stream().anyMatch(item -> containsCommand(item, blocked));
        if (value instanceof Iterable<?> list) {
            for (Object item : list) {
                if (containsCommand(item, blocked)) return true;
            }
        }
        return false;
    }
}
