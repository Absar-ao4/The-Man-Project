package com.example.fitapp.diet;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The price list and the user's own prices. Every read of an override is scoped to the user. */
@Repository
public class DietFoodRepository {

    /** The identity of a food row: its slug and the currency of that row. */
    public record FoodKey(String slug, String currency) {}

    private static final String SELECT =
            "select f.id, f.slug, f.name, f.category, f.diet_tags, f.allergens, f.kcal_per_100g, f.protein_per_100g,"
                    + " f.carbs_per_100g, f.fat_per_100g, f.unit, f.unit_grams, f.default_price, f.currency,"
                    + " f.serving_grams, f.max_servings, f.step_grams, f.meal_hint, u.price as user_price"
                    + " from food_prices f left join user_food_prices u"
                    + " on u.slug = f.slug and u.currency = f.currency and u.user_id = ?";

    private final JdbcTemplate jdbc;

    public DietFoodRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> supportedCurrencies() {
        return jdbc.queryForList("select distinct currency from food_prices order by currency", String.class);
    }

    /** Every food in [currency] with the user's price where they set one, ordered for display. */
    public List<DietFood> foodsFor(UUID userId, String currency) {
        return jdbc.query(SELECT + " where f.currency = ? order by f.category, f.name", (rs, i) -> map(rs), userId, currency);
    }

    /** The food row with this id, with the user's price. */
    public Optional<DietFood> find(UUID userId, UUID foodId) {
        return jdbc.query(SELECT + " where f.id = ?", (rs, i) -> map(rs), userId, foodId).stream().findFirst();
    }

    /** The slugs for these food ids (ids that do not exist are ignored). */
    public Map<UUID, FoodKey> keysFor(Collection<UUID> ids) {
        Map<UUID, FoodKey> keys = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return keys;
        }
        String marks = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.query(
                "select id, slug, currency from food_prices where id in (" + marks + ")",
                rs -> {
                    keys.put(rs.getObject("id", UUID.class), new FoodKey(rs.getString("slug"), rs.getString("currency")));
                },
                ids.toArray());
        return keys;
    }

    public void setOverride(UUID userId, String slug, String currency, BigDecimal price) {
        jdbc.update(
                "insert into user_food_prices (user_id, slug, currency, price, updated_at) values (?, ?, ?, ?, now())"
                        + " on conflict (user_id, slug, currency) do update set price = excluded.price, updated_at = now()",
                userId,
                slug,
                currency,
                price);
    }

    public void clearOverride(UUID userId, String slug, String currency) {
        jdbc.update("delete from user_food_prices where user_id = ? and slug = ? and currency = ?", userId, slug, currency);
    }

    // ------------------------------------------------------------------ mapping

    private static DietFood map(ResultSet rs) throws SQLException {
        BigDecimal userPrice = rs.getBigDecimal("user_price");
        double defaultPrice = rs.getBigDecimal("default_price").doubleValue();
        return new DietFood(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("category"),
                strings(rs.getArray("diet_tags")),
                strings(rs.getArray("allergens")),
                rs.getBigDecimal("kcal_per_100g").doubleValue(),
                rs.getBigDecimal("protein_per_100g").doubleValue(),
                rs.getBigDecimal("carbs_per_100g").doubleValue(),
                rs.getBigDecimal("fat_per_100g").doubleValue(),
                rs.getString("unit"),
                rs.getBigDecimal("unit_grams").doubleValue(),
                defaultPrice,
                userPrice == null ? defaultPrice : userPrice.doubleValue(),
                userPrice != null,
                rs.getString("currency").trim(),
                rs.getBigDecimal("serving_grams").doubleValue(),
                rs.getInt("max_servings"),
                rs.getBigDecimal("step_grams").doubleValue(),
                rs.getString("meal_hint"));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String::valueOf).toList();
    }
}
