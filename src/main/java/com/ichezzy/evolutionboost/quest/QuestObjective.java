package com.ichezzy.evolutionboost.quest;

import java.util.*;

/**
 * Ein einzelnes Objective (Ziel) einer Quest.
 * Eine Quest kann mehrere Objectives haben, die alle erfüllt werden müssen.
 */
public class QuestObjective {
    private final String id;
    private final QuestType type;
    private final String description;
    private final int target;
    private final Map<String, Object> filter;

    public QuestObjective(String id, QuestType type, String description, int target, Map<String, Object> filter) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.target = target;
        this.filter = filter != null ? new HashMap<>(filter) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public QuestType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public int getTarget() {
        return target;
    }

    public Map<String, Object> getFilter() {
        return Collections.unmodifiableMap(filter);
    }

    // ==================== Filter-Helper ====================

    /**
     * Holt eine Liste von Strings aus dem Filter (z.B. species, aspects).
     */
    @SuppressWarnings("unchecked")
    public List<String> getFilterList(String key) {
        Object value = filter.get(key);
        if (value instanceof List<?>) {
            return (List<String>) value;
        }
        if (value instanceof String) {
            return List.of((String) value);
        }
        return Collections.emptyList();
    }

    /**
     * Holt einen String aus dem Filter.
     */
    public String getFilterString(String key) {
        Object value = filter.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Holt einen Integer aus dem Filter.
     */
    public Integer getFilterInt(String key) {
        Object value = filter.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Holt einen Boolean aus dem Filter.
     */
    public Boolean getFilterBoolean(String key) {
        Object value = filter.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    /**
     * Prüft ob ein Pokemon die Filter-Kriterien erfüllt.
     * @param species Pokemon-Species (lowercase)
     * @param primaryType Primärer Typ (lowercase)
     * @param secondaryType Sekundärer Typ (lowercase, kann null sein)
     * @param aspects Liste der Aspects (lowercase)
     * @param level Pokemon-Level
     * @param isShiny Ob shiny
     */
    public boolean matchesPokemon(String species, String primaryType, String secondaryType,
                                   List<String> aspects, int level, boolean isShiny) {
        // Species-Filter
        List<String> speciesFilter = getFilterList("species");
        if (!speciesFilter.isEmpty()) {
            boolean match = speciesFilter.stream()
                    .anyMatch(s -> s.equalsIgnoreCase(species));
            if (!match) return false;
        }

        // Type-Filter
        List<String> typeFilter = getFilterList("types");
        if (!typeFilter.isEmpty()) {
            boolean match = typeFilter.stream()
                    .anyMatch(t -> t.equalsIgnoreCase(primaryType) ||
                            (secondaryType != null && t.equalsIgnoreCase(secondaryType)));
            if (!match) return false;
        }

        // Aspect-Filter (alle angegebenen Aspects müssen vorhanden sein)
        List<String> aspectFilter = getFilterList("aspects");
        if (!aspectFilter.isEmpty()) {
            for (String requiredAspect : aspectFilter) {
                boolean found = aspects.stream()
                        .anyMatch(a -> a.equalsIgnoreCase(requiredAspect));
                if (!found) return false;
            }
        }

        // Level-Filter
        Integer minLevel = getFilterInt("minLevel");
        if (minLevel != null && level < minLevel) return false;

        Integer maxLevel = getFilterInt("maxLevel");
        if (maxLevel != null && level > maxLevel) return false;

        // Shiny-Filter
        Boolean shinyRequired = getFilterBoolean("shiny");
        if (shinyRequired != null && shinyRequired && !isShiny) return false;

        return true;
    }

    /**
     * Prüft ob ein Item die Filter-Kriterien erfüllt.
     * @param itemId Item-ID (z.B. "evolutionboost:holy_spark")
     */
    public boolean matchesItem(String itemId) {
        String itemFilter = getFilterString("item");
        if (itemFilter != null) {
            return itemFilter.equalsIgnoreCase(itemId);
        }
        return true; // Kein Filter = alles matched
    }

    @Override
    public String toString() {
        return String.format("QuestObjective{id='%s', type=%s, target=%d}", id, type, target);
    }
}
