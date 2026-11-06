# 构建与静态验证记录

- 构建时间：2026-11-03（3.1.0 AI Agent 第一步）
- 构建命令：
  ```bash
  ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
  ./gradlew :app:assembleDebug \
    -Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2 --console=plain
  ```
- 结果：`BUILD SUCCESSFUL`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 包名：`com.dsha.sagittarius`
- versionCode/versionName：`61 / 4.2.2`
- minSdk / targetSdk / compileSdk：`26 / 34 / 34`
- 单元测试：`testDebugUnitTest` 通过（39 项，0 失败）
- Lint：`lintDebug` 通过（0 errors / 376 warnings，均为既有 warning）
- APK 权限（`aapt dump badging` 实测）：
  - `SYSTEM_ALERT_WINDOW`
  - `POST_NOTIFICATIONS`
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_SPECIAL_USE`
  - `INTERNET`
  - `ACCESS_COARSE_LOCATION`
  - `ACCESS_FINE_LOCATION`
  - `WAKE_LOCK`（WorkManager 依赖）
  - `com.dsha.sagittarius.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（AndroidX 动态接收器）
- 应用名：`亮丫生活`；启动 Activity：`com.dsha.sagittarius.ui.MainActivity`

## 本轮功能验证

### 三套主题

- `values/attrs.xml` 定义三套主题共用 `bl*` token；`ThemeTokens` 统一解析。
- `Theme.Sagittarius.Black`（默认）、`.Light`、`.Glass` 三套 style 均提供完整 token；`Theme.Sagittarius.Light` 用 Base + v27 导航栏覆盖。
- `ThemeManager` 使用 DataStore `sagittarius_theme` / `theme_mode`；默认黑色；`fromStorage` 单元测试覆盖 `light/black/glass/未知值`。
- `ThemedActivity` 在 `onCreate` 前 `setTheme`；主题 StateFlow 变化时 `recreate()`，不是进程重启；五个 Tab 由 ViewPager2/FragmentStateAdapter 保留。
- 悬浮窗服务监听主题变化，使用 `ContextThemeWrapper` 重建 overlay，并保留展开态。
- Android 12+ Glass：Activity 使用 `windowIsTranslucent`、`windowShowWallpaper`、`FLAG_BLUR_BEHIND`、`setBackgroundBlurRadius(42dp)`；悬浮窗已移除系统模糊，避免 HyperOS 整屏模糊。
- Android ≤30 Glass：`values/` 静态不透明玻璃背景（多层渐变 + 噪点 + 高光），无壁纸，保留可读 scrim。
- 五个 Tab 与协议页标题改为普通 TextView（sans-serif-medium + 主题文字色），移除标题渐变/描边/流光。

### 分类

- 预置分类稳定 ID：餐饮 1、交通 2、购物 3、娱乐 4、居住 5、医疗 6、教育 7、其他 8、日用 9、书籍 10、游戏 11。
- `CategoryEntity` 自定义分类支持新增、重命名、改色/图标；预置分类只读。
- 记一笔页只保留一个“＋”胶囊；长按自定义类别 Chip 在记一笔/历史筛选/统计筛选中重命名或删除；设置页不再提供独立管理页。
- 分类被记账引用时禁止删除；仅被预算树引用时清除节点关联后删除。
- 历史筛选、分类占比、编辑、CSV、悬浮窗快速记账、预算树关联均按 `categoryId`。
- Room 1→2 迁移在内存 SQLite 中模拟执行：旧 `category` 字符串正确映射到稳定 ID，旧记账、预算、AppState 行数保持不变；新增表/索引与 Room v2 schema 一致。

### 预算树

- `BudgetNodeEntity` / `budget_nodes` 表，按当前范围 start/end 读取；`parentId=null` 为一级，一级下的节点为二级，禁止第三层。
- 仓库校验：一级合计 ≤ 总预算；子项合计 ≤ 父节点；调低总预算低于一级合计时拒绝；超额返回 `OverAllocated`，界面提示可分配金额。
- 节点已用 = 关联类别在当前范围的支出合计；节点剩余 = 分配 − 已用；未分配 = 总预算 − 一级合计。
- 原始总剩余公式保持不变：总预算 − 全部支出。
- `BudgetTreeView` 自绘球状根/一级/二级节点、渐变边缘连线、球下分配/已用比例；点击展开/收起带淡入位移与 SpringAnimation；长按管理；超分配红色脉动、接近上限橙黄描边；低内存/移除动画时静态显示。
- 预算 Tab 保留上方剩余概览/进度，下方为预算树与“新增一级分配”。

### 悬浮迷你胶囊

- 窗口尺寸：**86dp × 29dp**（含四周 5dp 热区）。
- 视觉胶囊：**76dp × 19dp**，全圆角 = 高度 / 2。
- 收起态仅显示剩余金额（超支为负/警示色），`autoSizeTextType` 7–11sp，`tnum` 等宽数字；不绘制进度条。
- 点击胶囊弹簧展开；展开宽 344dp；收起按钮/点击外部流程不变；展开/收起以右边缘锚定。
- 收起/展开态均可拖动；收起态整个窗口含热区可拖，展开态 header/`⠿` 手柄可拖；位置限制在屏幕内。
- 主题色、玻璃渐变、描边、高光/噪点同步；收起态暂停液态色团并简化绘制；悬浮窗不使用系统背景模糊。
- 前台服务、通知、开关、快速记账、日期范围逻辑保持原实现。

### 2.8.3 默认值 / 历史拖拽与滑动删除

- 食物录入切换单位：g 默认 100，自定义单位默认为 1；切换时重置输入值；快捷添加复用同一逻辑。
- 历史饮食新增 DietHistoryAdapter：长按 ItemTouchHelper 跨餐次拖动、同餐次排序，选中项轻微放大；松手更新 mealType/createdAt 并写回 Room。
- 新增 SwipeRevealLayout：左滑超过一半露出红色删除按钮，点击按钮才删除；不足一半回弹；不弹提示文字；删除后 Snackbar 撤销。
- 备注/历史数据未改动。

### 2.8.2 快捷添加保存修复

- 根因：食材库快捷添加直接写数据库，但记饮食页 pending 为空，保存按钮判定为“无草稿”并红闪；气泡又把已保存记录合并展示，导致保存后仍不清空。
- 修复：快捷添加改为回调到记饮食页 pending 草稿；气泡只显示 pending；保存按钮统一写入当前日期并清空；食材库新增完成按钮。
- 已保存历史记录和营养页数据不受影响。

### 2.8.1 交互手感

- 金额输入 IME 下一项跳备注，备注完成触发保存；保存成功收起键盘、清空气泡并触发轻震动；空金额保存气泡红闪 + 警告震动。
- 类别单选覆盖：无新金额时点另一类别替换当前类别；选中类别高亮、长按移除；旧金额不再累加。
- 轮盘边缘橡皮筋 + 撞墙触觉；CategoryWheel 原有 Scroller 惯性保留；顶栏拖动超过半屏才提交，松手 SpringAnimation 回弹，锁定/主题切换触觉。
- 删除列表滑动阈值 30%，删除后 Snackbar 提供 3 秒撤销；长按删除/食材删除等有 LONG_PRESS 触觉。
- 悬浮胶囊展开 Spring 缩放 Q 弹；键盘 adjustResize + 备注 requestRectangleOnScreen。

### 2.8.0 单位/分类/快捷添加/实时营养

- Room v5：food_units 独立单位表（g=1g，自定义单位 1个=60g 等），food_categories 分类表；迁移清除旧 weightPerUnit 错误值。
- 食材表单改为每 100g 明确后缀标签；单位换算模块可新增、修改、删除，g 单位受保护。
- 食材库分类可新增/重命名/删除；预置分类不可删；食材长按确认删除，历史记录不受影响。
- 食材库 + 快捷添加使用当前日期/餐次，不关闭食材库；DietAdd 通过记录 Flow 实时刷新气泡。
- 快捷录入/饮食录入使用同一个实时营养 BottomSheet：热量、六项营养、展开动画；测试 9 项通过。

### 2.7.0 自定义裁剪 / 食材换图

- 新增自绘 CropImageView：单指拖动、双指缩放、固定裁剪比例、重置；替换系统裁剪 Intent。
- 顶栏图片与食材图片统一使用自定义裁剪；图片保存到应用私有目录并持久化路径。
- 食材库已有食材点击图片进入编辑页，可重新上传图片并更新食材记录；收藏状态、排序和营养参数保留。

### 2.6.0 食材库/轮盘/营养图例/顶栏图片

- 饮食轮盘完全复用记账 CategoryWheelView 的开口环+直线 PathMeasure 路径、角度拖动、Scroller 惯性和长按拖拽排序；默认空槽。
- 食材库 BottomSheet 分类：水果蔬菜/肉禽蛋/面食/零食；星标收藏才进入轮盘；新增食材支持图片上传、系统裁剪、自定义营养参数。
- 营养环图例与百分比；按日期范围特殊营养目标；默认目标与特殊目标查询合并。
- 顶栏自定义颜色面板新增图片背景 Tab；图片裁剪 3:1，叠加磨砂遮罩；路径 DataStore 持久化。
- Room v3→v4：食材分类/收藏/图片字段与 nutrition_goal_overrides 表。

### 2.5.0 饮食双模式

- DataStore `AppMode` + 记账/饮食模式切换；MainActivity 动态切换底部 Tab 和 ViewPager2 Fragment 列表。
- Diet Room v3 迁移：foods / diet_records / nutrition_goals，36 种内置食物与可自定义食物。
- 记饮食：餐次、食物轨道、点击食物输入摄入量与单位、营养快照累加、保存到当前日期。
- 营养结构：Canvas 复合环（热量外圈橙色 + 碳水/脂肪/蛋白质/纤维/水）、目标 BottomSheet、按餐分组记录与滑动删除。
- 历史饮食与营养页复用日期控制台；天气/设置保持不变。

### 2.4.6 底边直接碎裂

- 雨天：PathMeasure 提取胶囊顶部与左右轮廓；随机左右分流、沿切线旋转；3-8dp 体积、大水滴更快、速度越快泪滴越长；主体为淡蓝白透明层并移除黑色阴影；水迹为两侧亮边/中心透明薄膜，3 秒蒸发；水滴到达底边随机位置后**不再立起悬挂**，直接碎成 4 颗透明微粒水花向四周抛射；低端机关闭水痕并画圆形水滴。
- 雪天：保留飘雪粒子，叠加顶部/左右不规则透明结霜带、径向边缘霜、冰晶细线和冰粉；低端机静态结霜、减少雪粒子。
- 天气页副标题改为“今天的天色，也替你记一笔。”居中；删除“灵动岛微动效预览”标签；记一笔/历史/预算/统计/设置描述统一居中并改为文艺短句。
- 所有水滴/水痕/结霜 Path、Paint、Shader 初始化复用，绘制期不 new 对象。

### 2.4.2 电影级雨天/多云特效

- 雨天：预渲染贝塞尔曲线雨丝（LinearGradient + BlurMaskFilter 景深）；多层 RadialGradient 泪滴形水珠、拖尾水痕、底部飞溅；边缘 Fresnel 提亮。
- 多云：多重 RadialGradient 体积云、OVERLAY/SoftLight 混合（API29+）、呼吸缩放、丁达尔光斑；无硬边界。
- 全局：静态胶片噪点 Shader（2%-5%），Paint 抗锯齿；ValueAnimator/Choreographer 跟随刷新率。
- 低端机：关闭 BlurMaskFilter/BlendMode/噪点，雨丝改实线，水珠单层，云层减少到 2 层；所有 Bitmap/Shader/Paint 初始化复用。

### 2.4.1 折线锁持久化/天气打磨/预算树液体

- `ChartLockManager` 用独立 DataStore 持久化日销折线锁定状态；StatsFragment 重建后恢复按钮和图表锁定。
- 天气特效打磨：雨滴改为带拖尾的椭圆水珠，积雪增加下方冷色阴影与顶部亮点，阳光改为径向暖光，云影保持低透明度。
- 预算树液体：根球按总支出/总预算、子球按 `usedShare` 从底部上涨，带波浪液面与泡沫线；颜色优先取节点 `categoryId` 对应类别色，无关联时用主题渐变。

### 2.4.0 天气系统与灵动岛

- 新增第六个天气 Tab（统计与设置之间），ViewPager2 可滑动进入；底部未选中 Tab 只显示图标，选中显示标签。
- `WeatherRepository`：Open-Meteo 真实天气 + 30 分钟缓存；定位或城市预设；Debug 强制天气优先级最高；DataStore 持久化 selectedCity/effectsEnabled/debugOverride。
- 天气页：城市配置、实时天气、特效开关、下拉/按钮强制刷新；城市搜索 BottomSheet；Debug BottomSheet 强制晴/雨/雪/多云/真实。
- 顶栏城市控制台：天气页大号日期替换为城市名，左右滑动切换城市；锁定/范围按钮隐藏，颜色按钮保留。
- 天气特效：`WeatherEffectRenderer` 绘制雨丝/水滴、顶部积雪/雪花、阳光流光、云层暗影，使用 clipPath 限制在顶栏圆角内；悬浮窗 `WeatherCapsuleEffectView` 提供小型粒子点缀。
- 特效暂停：DateConsoleView 仅在 RESUMED 且可见时运行，浮动窗视图 detach 停止；减少动画/低内存用静态效果；不拦截触摸。

### 2.3.1 单轨类别轮盘

- 类别轨道改为单条连续 Path：段 A 为左下 30° 起、顺时针 270° 到右下 30° 的圆弧；段 B 从端点向右水平伸出屏幕。
- 所有类别按 `PathMeasure` 等距排列；第 9 个以后进入段 B 隐藏区，右侧渐隐/裁剪。
- 顺时针拖动 `offsetDistance` 到 0 卡死；逆时针拖动把直线段类别拉回圆环；使用 `Scroller` 惯性。
- 保留固定大小小球、点击累加、气泡明细/标签撤销、已记角标、飞入动效。

### 2.3.0 螺旋类别与渐变顶栏

- 类别改为单条连续轨道：段 A 为左下 30° 起顺时针 270° 到右下 30° 的圆弧，段 B 从端点水平向右伸出屏幕；所有类别用 `PathMeasure` 等距排列；第 9 个以后进入段 B 隐藏区，右侧渐隐/裁剪。
- 气泡内部改为多子项：点击类别把当前金额归入子项，同类别累加；气泡显示总金额、可点击撤销的类别标签、撤销和清空按钮；小球显示“已记 ¥xx”角标、微光和飞入动效。
- 保存通过 `addExpenses` 事务写入同一天多条明细，总金额与子项之和一致，沿用原有预算/统计计算。
- 顶栏新增极光绿/晚霞红/赛博紫/晨曦蓝/流沙金 5 种渐变；剪影图标使用主色推导的高对比 tint。
- 螺旋拖拽继续禁止 ViewPager2 拦截，旋转帧使用 `postInvalidateOnAnimation()`。

### 2.2.1 顶栏方向与记一笔精简

- 修正顶栏水平手势：左滑 `dx<0` → 日期 +1 周期（未来），右滑 → 日期 -1 周期（过去）；两侧预览文字按同一方向重排。
- 上下滑动保持“上滑放大（天→月→年）、下滑缩小”。
- 删除记一笔“消费日期”标签和日期选择行；保存时直接使用 `DateConsoleManager` 当前聚焦日期，自定义范围取起始日。
- 删除日期行后压缩轮盘与备注区间距，气泡球与类别轮盘保持居中。

### 2.2.0 记一笔气泡球与顶栏升级

- 金额输入改为 `BubbleAmountView` 透明磨砂气泡球，点击唤起系统键盘，金额 Canvas 居中绘制。
- 类别改为 `CategoryWheelView` 环绕轮盘：左下 210° 起顺时针排布，右下 150° 后 30% 透明度；逆时针拖动旋转，焦点类别放大高亮。
- 长按类别 500ms：静止松手弹出改名/删除菜单；移动进入拖拽换位，松手写入 `sortOrder` 持久化。
- 自定义类别删除时，关联记账与预算树类别自动转为“其他”，不再因占用而阻止删除。
- 顶栏大日期左右增加相邻周期淡化预览，拖动时同步位移/渐变；日期改为 `sans-serif-light` + `tabular-nums`。
- 锁定/日期/颜色三个按钮改为单色矢量剪影，随顶栏自定义色动态 tint。
- 设置页删除“桌面与兼容”整块及小部件/HyperOS 子项，保留的标题与板块居中排列。

### 2.1.0 顶栏日期控制台

- 四个页面（记一笔/历史/预算/统计）使用统一磨砂玻璃 `DateConsoleView`；设置页保持普通标题。
- 小页面名 + 大日期 + 🔒 / 📅 / 🎨 三个按钮；颜色预设写入独立 DataStore，重启保留。
- 每页独立 `DateConsoleManager` 状态：添加页默认日，历史/预算/统计默认月；左右按量程切换日期，上下切换日/月/年。
- 顶栏拖动时 `requestDisallowInterceptTouchEvent(true)`，锁定后不改变日期；ViewPager2 翻页只在顶栏外触发。
- 预算页同步激活日期范围；历史页同步列表筛选；统计页同步汇总/饼图/日销起始月；添加页同步表单日期。
- 日销折线图 header 新增独立锁定按钮，锁定后图表拖动与翻页手势均被拦截。

### 2.0.0 新增功能

- 应用显示名改为「Sagittarius」，包名继续使用 `com.dsha.sagittarius`，保留旧数据和签名升级路径。
- 底部 Tab 从 4 个扩展为 5 个：记一笔、历史记账、预算、统计面板、系统设置；主容器改为 ViewPager2 + FragmentStateAdapter，支持点击/左右滑动同步。
- 标题改为普通 TextView；预算预设改为本月/下个月/本周/近7天/自定义；App 图标替换为附件图。
- 主容器改为 ViewPager2 + FragmentStateAdapter；日销图/预算树拖动时禁止 ViewPager2 拦截。
- 自定义类别：
  - 记一笔页只保留一个“＋”胶囊作为新增入口；
  - 轻量弹层支持名称、颜色、图标；保存后自动选中；
  - 长按自定义类别 Chip 支持重命名/删除（记一笔、历史筛选、统计筛选三处）；
  - 有记账引用时禁止删除；移除设置页独立类别管理入口；Room 字段未改，复用 `iconKey`。
- 预算树：
  - `BudgetTreeView` 重写为球形节点 + 渐变连线；
  - 根/一级/二级用球径区分；自动分层布局避免重叠；
  - 点击展开/收起，子球淡入 + 位移 + Spring 回弹；长按编辑；
  - 接近上限橙黄描边，超额红色描边 + 脉动；根球附近显示未分配；
  - 低端机关闭脉动/展开动画，球内文字过小时只显示金额。
- 统计面板：
  - 日期范围（本月/下个月/本周/近 7 天/自定义）+ 类别多选；
  - 日销折线图按月分页、OverScroller 惯性滚动、无限拖动、整月吸附、动态刻度；
  - 汇总卡片 + 消费比伪 3D 饼图 + 图例；
  - 折线节点点击 tooltip、饼图扇区点击高亮/详情；
  - 小占比合并“其他”；低端机饼图降级 2D；
  - 三个图表数据全部来自 `observeAllExpenses` / `observeCategories` / `BudgetMath.summarize`，没有新增数据源；
  - 浅色主题使用加深图表色，Glass 主题图表区绘制低透明度蒙层。
- 未改：预算剩余公式、Room 表结构、主题 token 体系、悬浮窗服务主体、桌面小部件结构。

### 1.3.1–1.3.4 闪退与悬浮窗可用性修复

- 真机 logcat 定位：`GradientActionButton.<init>` 期间 `TextView` 调 `setEnabled()`，触发重写的 `drawableStateChanged()` → `updateTextColor()`，此时 Kotlin 属性 `tokens` 还未初始化，抛 `NullPointerException`，导致 `fragment_add_expense` 膨胀失败。
- 修复：`GradientActionButton` 增加 `tokensReady` 守卫，super 构造期间的 `drawableStateChanged()` 直接返回；`init` 完成 `tokens` 初始化后再着色。
- 修复后重新构建，单元测试 7 项、Lint 0 error 通过。
- v1.3.2 真机 logcat 又定位到第二处：`fragment_add_expense` 的 `GridLayout` 固定 `rowCount=2`，11 个预置分类 + “＋自定义”共 12 个 chip 需要 3 行，`addView` 时抛 `row indices (start + span) mustn't exceed the row count`。已移除固定 `rowCount`，按内容自动扩行。
- v1.3.3 真机 logcat：悬浮窗启动时 `renderRecent()` 用 `LayoutInflater.from(this)`（Service 默认 `Theme.DeviceDefault.Light.DarkActionBar`）膨胀 `item_recent_expense`，`ImageView` 的 `?attr/blIconCircleBackground` 无法解析，抛 `UnsupportedOperationException`。已缓存悬浮窗的 `ContextThemeWrapper`，最近记录行改用同一主题 inflater。
- v1.3.4 真机反馈：展开态窗口过高、系统悬浮窗模糊被 HyperOS 扩展到整屏，且缺少关闭入口、进入 App 自动打开。修复：
  - 展开高度限制为屏幕 72%，`expandedBody` 改为 `ScrollView`，内容可滚动；
  - 移除悬浮窗 `FLAG_BLUR_BEHIND` / `setBlurBehindRadius`，保留静态多层玻璃，避免整屏模糊和卡顿；
  - header 增加“关闭”按钮，关闭时隐藏 overlay、清除 `floatingEnabled` 并停止前台服务；
  - MainActivity 移除进入 App 自动启动逻辑；服务改为 `START_NOT_STICKY`，空 Intent 不自动弹出；状态残留会自动校正为关闭。
- 由于 Shizuku 设备命令策略只允许 `pm` 查询、不允许安装，未能自动覆盖安装；固定 APK 已导出到手机 `Download/DSHA/Sagittarius-2.0.1.apk`，需手动点按安装。

### 未执行项

- Shizuku 设备命令策略只允许 `pm` 查询、不允许安装，因此 2.0.0 未能自动覆盖安装；APK 已导出到手机 `Download/DSHA/Sagittarius-2.0.1.apk`，需手动点按安装后做视觉验收。
- 真机视觉验收重点：五个 Tab、统计折线/饼图点击与筛选动画、球状预算树展开/长按、三主题图表蒙层、低端机降级。
- Android 12+ Activity 窗口模糊是系统能力；HyperOS/MIUI 若关闭跨窗口模糊，会自动保留静态玻璃层，不崩溃。

## 命令

```bash
# 容器内需要 ARM AAPT2 shim
ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
./gradlew :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2

ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
./gradlew :app:testDebugUnitTest \
  -Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2

ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
./gradlew :app:lintDebug \
  -Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2
```

## 3.0.0 更名验证

- 项目根目录：`Sagittarius/`；`rootProject.name = "Sagittarius"`。
- 包名：`com.dsha.sagittarius`（`namespace` 与 `applicationId` 同步）；入口类 `SagittariusApp`；启动 Activity `com.dsha.sagittarius.ui.MainActivity`。
- 资源：`Theme.Sagittarius.*` / `ThemeOverlay.Sagittarius.*` / `TextAppearance.Sagittarius.*`；应用名 `Sagittarius`，小部件名 `Sagittarius · 4x1`。
- 存储：Room 文件名 `sagittarius.db`；主题 DataStore `sagittarius_theme`；备份规则同步更新。
- 兼容：同包内旧库 `budget_left.db` 与旧主题 DataStore 首次启动自动复制；跨包（applicationId 变更）旧数据在旧包沙箱，系统不允许读取，需导出/导入或 root 搬运。
- 构建：`:app:assembleDebug` 成功；`:app:testDebugUnitTest` 9 项通过；`:app:lintDebug` 0 error。
- APK 实测：`aapt dump badging` 显示 `package: com.dsha.sagittarius`、`versionCode 30`、`versionName 3.0.0`、`application-label: Sagittarius`。
- 未改：既有 Room 表结构与迁移、`bl*` 主题 token 命名、业务类名（`BudgetRepository` / `BudgetFragment` 等领域命名保留）。


## 3.1.0 AI Agent 第一步验证

### 构建与测试

```bash
ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest \
    -Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2 --console=plain
```

- 结果：`BUILD SUCCESSFUL`。
- 单元测试 24 项，0 失败：既有 9 项 + SSE/LLM/Engine/Tool/Registry 15 项。
- `aapt dump badging`：`com.dsha.sagittarius`，`versionCode 31`，`versionName 3.1.0`，应用名 `亮丫生活`。

### 已实现

- FAB 与 `ApiKeySheet`；Keystore 加密保存，`/models` 校验；FragmentResult 直接进入对话。
- `SagittariusActivity`：模型/推理档位、消息列表、空态示例、停止/清空/更换 Key。
- `LlmClient`：OkHttp SSE，reasoning/content/tool_call 分片，错误映射；调试日志脱敏。
- `AgentEngine`：系统提示词逐轮注入、工具循环 ≤5、重复调用去重、工具超时、reasoning_content 回传。
- `add_expense` / `add_diet` 工具与窄端口适配器；不新增数据库表。
- Debug 日志：`adb logcat -s Sagittarius`。

### 未执行

- 没有真实 DeepSeek API Key，因此未进行真实联网对话/记账端到端验收；网络路径由 MockWebServer 单测覆盖。
- 没有真机 UI 点击截图；本轮完成编译、单测、Lint 与 aapt 校验。


## 3.1.0 多服务商补充验证

- ProviderRegistry：DeepSeek / 智谱 GLM / 硅基流动 / OpenRouter 四个配置；智谱 `canDisableThinking=false`，硅基流动 `Qwen/Qwen3-8B` 兜底。
- Key 按 provider 独立：DataStore 使用 `api_key_cipher_<provider>` / `api_key_iv_<provider>`；Keystore alias 同样分 provider。
- 模型/思考档位按 provider 持久化：`model_id_<provider>`、`thinking_<provider>`。
- `/models` 动态拉取，失败使用 fallback；GLM 强制思考请求只带 `thinking.type`，不发送 reasoning_effort。
- 工具 400 且错误含 tools/function 时，AgentEngine 自动重试纯聊天并提示降级。
- 历史裁剪 ≤24 条/32k 字符，按用户轮次移除，保留 assistant.tool_calls 与 tool 结果配对。
- MockWebServer 单测覆盖 DeepSeek/GLM 请求体差异、SSE 分片、401、tool_calls 累积。

- 构建/测试命令同上；结果 `BUILD SUCCESSFUL`，26 项单测通过；`lintDebug` 0 errors / 332 warnings。
- 未提供真实四家 API Key，未执行真实联网验收；无真机点击截图。


## 3.1.4 无返回内容修复

- `stream:false` 标准响应解析：`choices[0].message.content`；`content` 为空时回退 `reasoning_content`；空响应/非法 JSON/HTTP 错误均有具体错误。
- 连接 30s、读取 120s，避免慢模型被提前断开。
- 欢迎语与“正在思考…”占位。
- `SagittariusLogger` 保留最近 50 条日志；Debug 🐞 弹窗支持复制全部；同时写入 `Download/sagittarius-agent.log`。
- 29 项单测通过；Lint 0 errors / 335 warnings。
- 未配置真实服务商 Key，未做真实端到端联网；MockWebServer 覆盖标准 message、reasoning 兜底、401、JSON 错误。


## 3.1.5 扩展工具验证

- 工具注册：add_expense / add_diet / search_food_cn_local / get_cn_food_nutrition / web_search / manage_food_library / update_app_settings / query_app_data。
- 单测覆盖：在线搜索解析与空结果、食材库增删改、设置修改、预算/历史查询、时间餐次推断、System Prompt 时间注入。
- 构建命令同上；35 项单测通过；`lintDebug` 0 errors / 335 warnings。
- 未配置真实服务商 Key，联网搜索仅由 MockWebServer 验证。


## 3.1.6 食物降级闭环验证

- 核心工具：search_food_cn_local / get_cn_food_nutrition / web_search / manage_food_library。
- 单测：博查前三条摘要/失败/无 Key、工具注册、System Prompt 工作流。
- 37 项单测通过；Lint 0 errors / 336 warnings。
- 联网与博查 Key 未真实配置，网络接口由 MockWebServer 覆盖；UI 真机未点击验收。


## 3.1.7 UI/类别修复

- 齿轮设置 Sheet：provider/custom base URL/model/search provider/search key；保存后 FragmentResult 触发 Agent 重载。
- 气泡最大宽度 82%，文本 maxWidth 与 12dp padding，主题卡片色/描边。
- 友好错误映射，Debug 保留原始日志。
- manage_food_library schema 必填 category；Room 保存 FRUIT_VEG/MEAT_EGG/STAPLE/SNACK/OTHER。
- 37 项单测通过；Lint 0 errors / 336 warnings；未真机验收。


## 3.1.8 动效/推理条验证

- AnimatedMenuPopup：SpringAnimation Q 弹下拉；SheetMotion 统一 BottomSheet 入场。
- ReasoningFlowSlider：缓存 LinearGradient Shader，ValueAnimator 驱动 Matrix 平移，低内存设备关闭 Blur/粒子。
- 37 项单测通过；Lint 0 errors / 340 warnings。
- 未真机验证动画帧率与触感反馈。


## 3.1.9 按钮配色/排版验证

- ButtonAccentManager：5 预设 + 自定义 DataStore 持久化；FAB 使用 ColorStateList，其他主按钮使用动态渐变/文字对比色。
- 对话气泡 20dp 圆角、75% 最大宽度；顶部 Chip 横向滚动与省略。
- 推理条矩形滑块、26dp 轨道、右→左深度粒子、推理状态淡入淡出。
- 38 项单测通过；Lint 0 errors / 335 warnings；未真机验收。


## 3.1.10 记忆/语音/推理条验证

- Room `chat_history` v6 迁移：CREATE TABLE + createdAt index（schema 6 已生成）。
- Web 开关 DataStore 持久化；启动 ViewModel 恢复。
- 推理条 ACTION_UP 才提交，拖动不写 DataStore；七色渐变与 MAX Shader。
- 语音：RECORD_AUDIO 权限、RecognitionService query、SpeechRecognizer、VoiceAgentRunner 最终结论、历史落库。
- 38 单测通过；Lint 0 errors / 339 warnings；未真机验证语音识别服务与动画帧率。


## 3.1.11 本地营养库/语音验证

- npm cn-food-mcp@1.0.0 数据：1725 条、660 KB、25 项/100g；assets/cn_food.json；Room v7 cn_foods（6→7 迁移）。
- 本地工具无 HTTP；测试覆盖本地命中、未命中系统提示、25 项全量。
- 移除境外营养工具、URL、UI 与旧开关字段。
- 语音：延迟启动、2 次重试、8s 超时、静音参数；未真机验证识别服务。
- 39 单测通过；Lint 0 errors / 339 warnings。


## 3.1.12 交互修复验证

- 发送清空/键盘收起；停止按钮取消协程并 cancel OkHttp Call（invokeOnCompletion）。
- 输入区麦克风 + VoiceInputOverlay 回调填字并 700ms 自动发送；长按 ACTION_UP stopListening。
- 顶部 Chip 36dp / 12sp，横向滚动，系统栏 inset 保留。
- 39 单测通过；Lint 0 errors / 342 warnings；未真机验证语音服务与停止链路。


## 3.1.13 语音触摸修复

- 删除所有 ACTION_UP/ACTION_CANCEL → stopListening 的触摸绑定。
- 新状态机：RECORDING → 点击发送 → RECOGNIZING → RESULT；取消丢弃。
- 触摸监听不再依赖 FAB；Dialog 根视图独立。
- 39 单测通过；Lint 0 errors / 341 warnings；未真机麦克风验收。


## 3.1.14 语音权限日志修复

- 用户日志：SpeechRecognizer error 9（INSUFFICIENT_PERMISSIONS），非触摸焦点问题。
- 新增 RECORD_AUDIO + AppOps 检测、权限提示、去系统设置按钮。
- 录音与 FAB 触摸解绑；发送/完成按钮停止并识别。
- 39 单测通过；Lint 0 errors / 341 warnings；未真机验证授权后识别。


## 3.1.15 语音服务排查

- manifest queries += com.google.android.googlequicksearchbox。
- isRecognitionAvailable + isOnDeviceRecognitionAvailable；优先 on-device，失败回退云端。
- code=9 Toast 系统语音服务麦克风权限；系统 RecognizerIntent fallback。
- 39 单测通过；Lint 0 errors / 341 warnings；未真机验收。


## 3.2.0 Vosk 验证

- 代码中 SpeechRecognizer / RecognitionListener / RecognizerIntent 检索为空；manifest 无语音 queries。
- assets/vosk-model-cn final.mdl + Gr.fst + uuid 已打包；模型加载在 App 后台协程。
- 39 单测通过；Lint 0 errors / 348 warnings。
- 未真机验证 Vosk 模型兼容性、麦克风录音和 offline 识别。


## 3.2.1 输入法语音/设置面板

- Vosk/SpeechRecognizer/AudioRecord 代码与依赖检索为空；APK 约 8.9MB。
- IME 语音、AgentMoreSheet、权限 gate、消息删除、tokens 统计、剪影图标。
- 39 单测通过；Lint 0 errors / 366 warnings；未真机验证键盘语音。


## 3.5.0 结构化回复验证

- 思考过程折叠、工具微胶囊、ToolDetailsSheet、DETAILS 解析/折叠、人设 Prompt。
- 39 单测通过；Lint 0 errors / 371 warnings。
- 未真机验收输入法语音与复杂模型输出格式。


## 3.6.0 工作流/会话/富文本

- Room v8 chat_sessions + sessionId 迁移；会话切换与时间+首句命名。
- 工作流折叠 ToolTrace 胶囊；RichTextParser/RainbowSpan/ShakeSpan/RichTextView；DETAILS 卡片；思考提示轮换。
- 39 单测通过；Lint 0 errors / 376 warnings；未真机验收动画与输入法。


## 3.7.0 富文本/会话验证

- 颜色与 Shake/Spin 分离、glow/blink/spin、DETAILS、RichTextView 动画生命周期。
- 消息操作 BottomSheet、会话删除、清空当前对话。
- 39 单测通过；Lint 0 errors / 379 warnings；未真机验收动画/输入法。


## 3.8.0 Function Calling 验证

- 工具注册扩展、System Prompt 多意图/日期/自主联网、系统端口导航/主题/天气/悬浮窗、QueryData 结构化查询。
- 39 单测通过；Lint 0 errors / 379 warnings；未配置真实模型做端到端工具编排。


## 3.9.0 滚动/生动性验证

- 自动滚动底部判断、滑块刻度、AI 生动性、用户气泡强调色、思考提示浮动。
- 39 单测通过；Lint 0 errors / 381 warnings；未真机验收滚动流畅度与动画。


## 3.9.1 闪烁/FAB

- BlinkSpan 使用 0.2~1.0 透明度 ValueAnimator 呼吸。
- FAB 改为 56dp ImageButton，使用加工后的 RGBA sgr.png（紫蓝渐变圆牌、白/青描边），无默认背景/阴影/tint。
- 39 单测通过；Lint 0 errors / 382 warnings。


## 4.0.0 动效/桌宠验证

- 按钮 Spring/Overshoot/Haptic/Ripple；桌宠透明 PNG、浮动、余额/Tokens、toggle_pet。
- 39 单测通过；Lint 0 errors / 388 warnings；未真机验收桌宠布局与真实余额接口。


## 4.1.0 桌宠拖动/配置验证

- 400ms 长按拖动、PopupWindow 驱逐、位置持久化；右上角长按配置文字色与 50-200% 缩放；AI update_pet_config。
- 39 单测通过；Lint 0 errors / 394 warnings；未真机验收拖动坐标与 Popup 位置。


## 4.2.0 分类数据源验证

- categories 表 v9 字段/迁移、动态分类工具、预算树 category_id/父节点、统一 message 错误、精简 Prompt。
- 39 单测通过；Lint 0 errors / 395 warnings；未真机验证旧库迁移日志、全屏拖动/锁定与自定义分类统计。


## 4.2.1 分类/桌宠验证

- ExpenseCategory 枚举已删除；分类动态注入、manage_category、Room callback、v9 迁移。
- 桌宠全屏 X/Y 拖动、500ms 菜单、❌/🔒、单击余额刷新。
- 39 单测通过；Lint 0 errors / 395 warnings；未真机执行数据库迁移与触控验收。


## 4.2.2 桌宠/选项验证

- 左上角绝对坐标、X/Y 拖动、暂停呼吸动画、位置初始化；ChoiceParser、选项 UI、点击发送。
- 42 单测通过；Lint 0 errors / 395 warnings；未真机验收拖动与选项点击。
