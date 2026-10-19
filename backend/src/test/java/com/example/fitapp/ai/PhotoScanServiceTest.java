package com.example.fitapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fitapp.ai.PhotoScanService.Parsed;
import com.example.fitapp.ai.PhotoScanService.RawItem;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.nutrition.FoodData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhotoScanServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static FoodData usda(String id, double kcal) {
        return new FoodData("USDA", id, "Food " + id, null, kcal, 10, 10, 10, List.of());
    }

    private static RawItem item(double grams, double kcal) {
        return new RawItem("rice", grams, 0.8, kcal, 4, 45, 1);
    }

    // ------------------------------------------------------------ sanitising

    @Test
    void everythingTheModelSaysIsClamped() throws Exception {
        var input =
                mapper.readTree(
                        """
                        {"isFood":true,"notes":"ok","items":[
                          {"name":"  Rice  ","estimatedGrams":99999,"confidence":4,"calories":99999,"proteinG":9999,"carbsG":9999,"fatG":9999},
                          {"name":"Egg","estimatedGrams":-5,"confidence":-1,"calories":-10,"proteinG":-1,"carbsG":-1,"fatG":-1}
                        ]}
                        """);

        Parsed parsed = PhotoScanService.sanitize(input);

        RawItem rice = parsed.items().get(0);
        assertThat(rice.name()).isEqualTo("Rice");
        assertThat(rice.grams()).isEqualTo(2000);
        assertThat(rice.confidence()).isEqualTo(1);
        assertThat(rice.kcal()).isEqualTo(5000);
        assertThat(rice.protein()).isEqualTo(500);
        assertThat(rice.carbs()).isEqualTo(800);
        assertThat(rice.fat()).isEqualTo(500);
        RawItem egg = parsed.items().get(1);
        assertThat(egg.grams()).isEqualTo(1);
        assertThat(egg.confidence()).isEqualTo(0);
        assertThat(egg.kcal()).isEqualTo(0);
    }

    @Test
    void itemsWithoutANameOrAPortionAreDroppedAndTheListIsCapped() throws Exception {
        StringBuilder sb = new StringBuilder("{\"isFood\":true,\"items\":[");
        sb.append("{\"name\":\"\",\"estimatedGrams\":100},");
        sb.append("{\"name\":\"No grams\"},");
        for (int i = 0; i < 20; i++) {
            sb.append("{\"name\":\"Food ").append(i).append("\",\"estimatedGrams\":100,\"confidence\":0.9,\"calories\":100},");
        }
        sb.setLength(sb.length() - 1);
        sb.append("]}");

        Parsed parsed = PhotoScanService.sanitize(mapper.readTree(sb.toString()));

        assertThat(parsed.items()).hasSize(PhotoScanService.MAX_ITEMS);
        assertThat(parsed.items()).noneMatch(i -> i.name().isBlank() || i.name().equals("No grams"));
    }

    @Test
    void longNamesAreCutAndNotFoodHasNoItems() throws Exception {
        String longName = "x".repeat(200);
        var food = mapper.readTree("{\"isFood\":true,\"items\":[{\"name\":\"" + longName + "\",\"estimatedGrams\":50}]}");
        assertThat(PhotoScanService.sanitize(food).items().get(0).name()).hasSize(80);

        var notFood = mapper.readTree("{\"isFood\":false,\"items\":[{\"name\":\"Cat\",\"estimatedGrams\":50}]}");
        Parsed parsed = PhotoScanService.sanitize(notFood);
        assertThat(parsed.isFood()).isFalse();
        assertThat(parsed.items()).isEmpty();
    }

    @Test
    void aReplyThatIsNotAnObjectIsRefused() {
        assertThatThrownBy(() -> PhotoScanService.sanitize(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PhotoScanService.sanitize(mapper.createArrayNode())).isInstanceOf(ApiException.class);
    }

    @Test
    void averageConfidenceIsTheMeanAndZeroWithoutItems() {
        Parsed two = new Parsed(true, List.of(new RawItem("a", 100, 0.2, 100, 1, 1, 1), new RawItem("b", 100, 0.6, 100, 1, 1, 1)), "");
        assertThat(two.averageConfidence()).isEqualTo(0.4, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(new Parsed(true, List.of(), "").averageConfidence()).isZero();
    }

    // ------------------------------------------------------------ plausibility

    @Test
    void aUsdaFoodWithinTheDensityBandIsAccepted() {
        // The model says 130 kcal per 100 g (130 kcal in 100 g).
        assertThat(PhotoScanService.pick(item(100, 130), List.of(usda("1", 130))).orElseThrow().externalId()).isEqualTo("1");
        assertThat(PhotoScanService.pick(item(100, 130), List.of(usda("2", 130 * 0.4 + 1))).isPresent()).isTrue();
        assertThat(PhotoScanService.pick(item(100, 130), List.of(usda("3", 130 * 2.5 - 1))).isPresent()).isTrue();
    }

    @Test
    void aUsdaFoodOutsideTheBandIsRejectedAndTheNextCandidateIsTried() {
        assertThat(PhotoScanService.pick(item(100, 130), List.of(usda("1", 130 * 0.4 - 1)))).isEmpty();
        assertThat(PhotoScanService.pick(item(100, 130), List.of(usda("1", 130 * 2.5 + 1)))).isEmpty();
        assertThat(PhotoScanService.pick(item(100, 130), List.of(usda("bad", 900), usda("good", 140))).orElseThrow().externalId())
                .isEqualTo("good");
    }

    @Test
    void whenTheModelThinksTheFoodHasNoCaloriesTheRatioIsSkipped() {
        // 2 kcal in 100 g is below the 5 kcal per 100 g threshold: take the first candidate.
        assertThat(PhotoScanService.pick(item(100, 2), List.of(usda("water", 0))).orElseThrow().externalId()).isEqualTo("water");
    }

    @Test
    void noCandidatesMeansNoMatch() {
        assertThat(PhotoScanService.pick(item(100, 130), List.of())).isEmpty();
    }

    // ------------------------------------------------------------ image bytes

    @Test
    void magicBytesMustMatchTheDeclaredType() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8'};
        assertThat(PhotoScanService.matchesMagic("image/jpeg", jpeg)).isTrue();
        assertThat(PhotoScanService.matchesMagic("image/png", png)).isTrue();
        assertThat(PhotoScanService.matchesMagic("image/webp", webp)).isTrue();
        assertThat(PhotoScanService.matchesMagic("image/png", jpeg)).isFalse();
        assertThat(PhotoScanService.matchesMagic("image/jpeg", "hello world, not an image".getBytes())).isFalse();
        assertThat(PhotoScanService.matchesMagic("image/gif", jpeg)).isFalse();
    }
}
