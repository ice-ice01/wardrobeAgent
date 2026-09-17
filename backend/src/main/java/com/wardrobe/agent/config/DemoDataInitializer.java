package com.wardrobe.agent.config;

import com.wardrobe.agent.model.UserModel;
import com.wardrobe.agent.model.UserModelRepository;
import com.wardrobe.agent.user.AppUser;
import com.wardrobe.agent.user.AppUserRepository;
import com.wardrobe.agent.wardrobe.WardrobeItem;
import com.wardrobe.agent.wardrobe.WardrobeItemCommand;
import com.wardrobe.agent.wardrobe.WardrobeItemRepository;
import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
/** 应用启动后幂等创建 demo 用户、示例衣橱和预设模特，方便本地学习和联调。 */
public class DemoDataInitializer implements ApplicationRunner {
    private final AppUserRepository users;
    private final WardrobeItemRepository items;
    private final UserModelRepository models;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;

    public DemoDataInitializer(AppUserRepository users, WardrobeItemRepository items, UserModelRepository models,
                               PasswordEncoder passwords, ObjectMapper json) {
        this.users = users; this.items = items; this.models = models; this.passwords = passwords; this.json = json;
    }

    @Override
    @Transactional
    /** ApplicationRunner 会在 Spring 容器启动完成后执行；事务保证种子数据整体一致。 */
    public void run(ApplicationArguments args) {
        AppUser user = users.findByUsername("demo").orElseGet(() -> users.save(new AppUser("demo", passwords.encode("wardrobe123"), "林衣")));
        boolean wardrobeChanged = false;
        if (items.countByUserIdAndDeletedFalse(user.getId()) == 0) {
            seedLegacyWardrobe(user);
            wardrobeChanged = true;
        }
        wardrobeChanged |= seedPublicCatalog(user) > 0;
        if (wardrobeChanged) user.incrementWardrobeRevision();
        if (models.findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(user.getId()).isEmpty()) seedModels(user);
    }

    private void seedLegacyWardrobe(AppUser user) {
        List<SeedItem> seeds = List.of(
                new SeedItem("海军蓝格纹衬衫", "上装", WardrobeSlot.INNER_TOP, "海军蓝", "棉", Set.of("春", "秋"), Set.of("通勤", "旅行"), Set.of("休闲", "复古"), 2, 3),
                new SeedItem("紫色轻薄衬衫", "上装", WardrobeSlot.INNER_TOP, "紫色", "棉混纺", Set.of("春", "夏"), Set.of("通勤", "约会"), Set.of("休闲", "简约"), 1, 4),
                new SeedItem("灰色基础短袖", "上装", WardrobeSlot.INNER_TOP, "灰色", "棉", Set.of("春", "夏"), Set.of("休闲", "旅行"), Set.of("休闲", "简约"), 1, 5),
                new SeedItem("灰色连帽卫衣", "上装", WardrobeSlot.INNER_TOP, "灰色", "棉混纺", Set.of("春", "秋", "冬"), Set.of("休闲", "运动"), Set.of("运动", "街头"), 4, 2),
                new SeedItem("炭灰修身西装", "外套", WardrobeSlot.OUTER_TOP, "炭灰", "聚酯混纺", Set.of("春", "夏", "秋"), Set.of("面试", "婚礼", "通勤"), Set.of("正式", "简约"), 3, 3),
                new SeedItem("奶油色短款夹克", "外套", WardrobeSlot.OUTER_TOP, "奶油色", "聚酯纤维", Set.of("春", "夏", "秋"), Set.of("通勤", "旅行", "约会"), Set.of("休闲", "优雅"), 2, 4),
                new SeedItem("蓝色圆领针织衫", "外套", WardrobeSlot.OUTER_TOP, "蓝色", "针织", Set.of("春", "秋", "冬"), Set.of("通勤", "约会"), Set.of("简约", "休闲"), 4, 2),
                new SeedItem("深灰直筒西裤", "下装", WardrobeSlot.BOTTOM, "深灰", "聚酯混纺", Set.of("春", "夏", "秋"), Set.of("面试", "通勤", "婚礼"), Set.of("正式", "简约"), 2, 3),
                new SeedItem("米色休闲短裤", "下装", WardrobeSlot.BOTTOM, "米色", "棉", Set.of("春", "夏"), Set.of("海边", "旅行", "休闲"), Set.of("休闲", "简约"), 1, 5),
                new SeedItem("蓝色直筒牛仔裤", "下装", WardrobeSlot.BOTTOM, "蓝色", "牛仔", Set.of("四季"), Set.of("休闲", "旅行"), Set.of("休闲", "复古"), 3, 3),
                new SeedItem("棕色休闲短裤", "下装", WardrobeSlot.BOTTOM, "棕色", "棉混纺", Set.of("春", "夏"), Set.of("海边", "旅行", "休闲"), Set.of("自然", "休闲"), 1, 5),
                new SeedItem("灰色简约连衣裙", "连衣裙", WardrobeSlot.DRESS, "灰色", "棉混纺", Set.of("春", "秋"), Set.of("约会", "旅行", "通勤"), Set.of("简约", "休闲"), 2, 3),
                new SeedItem("黑色商务皮鞋", "鞋", WardrobeSlot.SHOES, "黑色", "皮革", Set.of("四季"), Set.of("面试", "通勤", "婚礼"), Set.of("正式", "简约"), 2, 2),
                new SeedItem("白色休闲运动鞋", "鞋", WardrobeSlot.SHOES, "白色", "织物", Set.of("春", "夏", "秋"), Set.of("休闲", "旅行", "运动"), Set.of("休闲", "运动"), 1, 5),
                new SeedItem("卡其色休闲鞋", "鞋", WardrobeSlot.SHOES, "卡其", "帆布", Set.of("春", "夏", "秋"), Set.of("休闲", "旅行"), Set.of("休闲", "简约"), 1, 4),
                new SeedItem("黑色户外凉鞋", "鞋", WardrobeSlot.SHOES, "黑色", "合成革", Set.of("夏"), Set.of("海边", "旅行", "休闲"), Set.of("运动", "休闲"), 1, 5),
                new SeedItem("银色简约腕表", "配饰", WardrobeSlot.ACCESSORY, "银色", "金属", Set.of("四季"), Set.of("面试", "通勤", "婚礼"), Set.of("简约", "正式"), 1, 5),
                new SeedItem("棕色轻薄围巾", "配饰", WardrobeSlot.ACCESSORY, "棕色", "聚酯纤维", Set.of("春", "夏", "秋"), Set.of("约会", "旅行"), Set.of("休闲", "优雅"), 1, 4)
        );
        int index = 1;
        for (SeedItem seed : seeds) {
            WardrobeItemCommand command = new WardrobeItemCommand(seed.name, seed.category, seed.slot, seed.color, seed.material,
                    seed.seasons, seed.scenes, seed.styles, seed.warmth, seed.breathability, null);
            items.save(new WardrobeItem(user.getId(), command, String.format("/assets/seed/item-%02d.jpg", index++), "CATALOG", null));
        }
    }

    /**
     * 按清单补齐公开教学目录。包含已软删除记录做幂等判断，避免用户删除后重启又恢复。
     */
    private int seedPublicCatalog(AppUser user) {
        List<CatalogSeedItem> catalog = readPublicCatalog();
        if (catalog.isEmpty()) return 0;
        validatePublicCatalog(catalog);
        Set<String> knownPaths = new HashSet<>();
        for (WardrobeItem item : items.findAllByUserIdOrderByCreatedAtAsc(user.getId())) {
            knownPaths.add(item.getImageUrl());
        }

        int inserted = 0;
        for (CatalogSeedItem seed : catalog) {
            if (!knownPaths.add(seed.localPath())) continue;
            WardrobeItemCommand command = new WardrobeItemCommand(seed.name(), seed.category(), seed.slot(),
                    seed.color(), seed.material(), seed.seasonTags(), seed.sceneTags(), seed.styleTags(),
                    seed.warmthLevel(), seed.breathabilityLevel(), null);
            items.save(new WardrobeItem(user.getId(), command, seed.localPath(), "CATALOG", null));
            inserted++;
        }
        return inserted;
    }

    /**
     * 从 classpath 读取结构化商品清单，并让 Jackson 将 JSON 数组反序列化为 Java record 列表。
     * classpath 指打包进应用的资源目录；本项目中对应 {@code src/main/resources}。
     */
    private List<CatalogSeedItem> readPublicCatalog() {
        ClassPathResource resource = new ClassPathResource("static/assets/seed/public-catalog-manifest.json");
        if (!resource.exists()) return List.of();
        try (var input = resource.getInputStream()) {
            return json.readValue(input, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read public catalog manifest", exception);
        }
    }

    /**
     * 在写数据库前校验教学目录，尽早暴露商品数量不足、图片路径重复或品类覆盖不足的问题。
     * 这类确定性数据规则应由 Java 保证，不应交给 AI 猜测或修复。
     */
    private static void validatePublicCatalog(List<CatalogSeedItem> catalog) {
        if (catalog.size() < 100) throw new IllegalStateException("Public catalog must contain at least 100 items");
        Map<WardrobeSlot, Integer> counts = new EnumMap<>(WardrobeSlot.class);
        Set<String> paths = new HashSet<>();
        for (CatalogSeedItem item : catalog) {
            counts.merge(item.slot(), 1, Integer::sum);
            if (!paths.add(item.localPath())) throw new IllegalStateException("Duplicate catalog path: " + item.localPath());
            if (!item.localPath().matches("/assets/seed/catalog-[0-9]{3}\\.jpg")) {
                throw new IllegalStateException("Unexpected catalog path: " + item.localPath());
            }
        }
        for (WardrobeSlot slot : WardrobeSlot.values()) {
            if (counts.getOrDefault(slot, 0) < 10) {
                throw new IllegalStateException("Public catalog slot must contain at least 10 items: " + slot);
            }
        }
    }

    private void seedModels(AppUser user) {
        UserModel first = new UserModel(user.getId(), "日常自然模特", "PRESET", null, "/assets/seed/model-01.png", true);
        first.setDefaultModel(true); models.save(first);
        models.save(new UserModel(user.getId(), "通勤中性模特", "PRESET", null, "/assets/seed/model-02.png", true));
        models.save(new UserModel(user.getId(), "休闲活力模特", "PRESET", null, "/assets/seed/model-03.png", true));
    }

    private record SeedItem(String name, String category, WardrobeSlot slot, String color, String material,
                            Set<String> seasons, Set<String> scenes, Set<String> styles, int warmth, int breathability) {}

    /**
     * JSON 清单中单件商品的数据结构。record 适合只承载不可变数据，Jackson 会按同名字段完成映射。
     */
    private record CatalogSeedItem(int index, String sourceItemId, String name, String category, WardrobeSlot slot,
                                   String color, String material, Set<String> seasonTags, Set<String> sceneTags,
                                   Set<String> styleTags, int warmthLevel, int breathabilityLevel,
                                   String localPath, String sourceDataset, String sourcePage, String license) {}
}
