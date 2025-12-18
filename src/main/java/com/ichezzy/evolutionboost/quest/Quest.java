package com.ichezzy.evolutionboost.quest;

import java.util.*;

/**
 * Eine Quest-Definition.
 * Enthält alle Informationen über eine Quest (nicht den Spieler-Fortschritt).
 */
public class Quest {
    private final String id;
    private final String questLine;         // z.B. "christmas", "halloween", "main"
    private final String name;
    private final String description;
    private final QuestCategory category;
    private final List<String> prerequisites;   // Quest-IDs die vorher abgeschlossen sein müssen
    private final List<QuestObjective> objectives;
    private final List<QuestReward> rewards;
    private final boolean autoActivate;     // Automatisch aktivieren wenn prerequisites erfüllt
    private final boolean hidden;           // Quest versteckt bis aktiviert
    private final int sortOrder;            // Für Sortierung in Listen

    private Quest(Builder builder) {
        this.id = builder.id;
        this.questLine = builder.questLine;
        this.name = builder.name;
        this.description = builder.description;
        this.category = builder.category;
        this.prerequisites = List.copyOf(builder.prerequisites);
        this.objectives = List.copyOf(builder.objectives);
        this.rewards = List.copyOf(builder.rewards);
        this.autoActivate = builder.autoActivate;
        this.hidden = builder.hidden;
        this.sortOrder = builder.sortOrder;
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    /**
     * Vollständige Quest-ID inkl. Questline (z.B. "christmas:mq1")
     */
    public String getFullId() {
        return questLine + ":" + id;
    }

    public String getQuestLine() {
        return questLine;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public QuestCategory getCategory() {
        return category;
    }

    public List<String> getPrerequisites() {
        return prerequisites;
    }

    public List<QuestObjective> getObjectives() {
        return objectives;
    }

    public List<QuestReward> getRewards() {
        return rewards;
    }

    public boolean isAutoActivate() {
        return autoActivate;
    }

    public boolean isHidden() {
        return hidden;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * Findet ein Objective nach ID.
     */
    public Optional<QuestObjective> getObjective(String objectiveId) {
        return objectives.stream()
                .filter(o -> o.getId().equals(objectiveId))
                .findFirst();
    }

    /**
     * Findet alle Objectives eines bestimmten Typs.
     */
    public List<QuestObjective> getObjectivesByType(QuestType type) {
        return objectives.stream()
                .filter(o -> o.getType() == type)
                .toList();
    }

    @Override
    public String toString() {
        return String.format("Quest{id='%s', name='%s', category=%s, objectives=%d}",
                getFullId(), name, category, objectives.size());
    }

    // ==================== Builder ====================

    public static Builder builder(String questLine, String id) {
        return new Builder(questLine, id);
    }

    public static class Builder {
        private final String id;
        private final String questLine;
        private String name = "Unnamed Quest";
        private String description = "";
        private QuestCategory category = QuestCategory.SIDE;
        private final List<String> prerequisites = new ArrayList<>();
        private final List<QuestObjective> objectives = new ArrayList<>();
        private final List<QuestReward> rewards = new ArrayList<>();
        private boolean autoActivate = false;
        private boolean hidden = false;
        private int sortOrder = 0;

        private Builder(String questLine, String id) {
            this.questLine = questLine;
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(QuestCategory category) {
            this.category = category;
            return this;
        }

        public Builder prerequisite(String questId) {
            this.prerequisites.add(questId);
            return this;
        }

        public Builder prerequisites(List<String> questIds) {
            this.prerequisites.addAll(questIds);
            return this;
        }

        public Builder objective(QuestObjective objective) {
            this.objectives.add(objective);
            return this;
        }

        public Builder objectives(List<QuestObjective> objectives) {
            this.objectives.addAll(objectives);
            return this;
        }

        public Builder reward(QuestReward reward) {
            this.rewards.add(reward);
            return this;
        }

        public Builder rewards(List<QuestReward> rewards) {
            this.rewards.addAll(rewards);
            return this;
        }

        public Builder autoActivate(boolean autoActivate) {
            this.autoActivate = autoActivate;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public Quest build() {
            if (objectives.isEmpty()) {
                throw new IllegalStateException("Quest must have at least one objective");
            }
            return new Quest(this);
        }
    }
}
