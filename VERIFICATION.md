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


## 1.0 维护基线（2026-11-17）

- 环境：Windows 11、JDK 17、Android SDK 34；命令与注意事项见 `BUILDING.md`。
- `assembleDebug --rerun-tasks`：BUILD SUCCESSFUL，40s，42/42 task 实际执行。
- `testDebugUnitTest --rerun-tasks`：17 个测试类 / 42 用例 / 0 失败 / 0 跳过，BUILD SUCCESSFUL，36s。
- `lintDebug`：0 error / 394 warning（UnusedResources 181、HardcodedText 44、SmallSp 20、DrawAllocation 18 等）。仅记录基线，本轮未为消除 warning 改代码。
- Kotlin 编译警告：16 条（既有问题，本轮未处理）。
- 修复：`ApiKeyStore.kt` 此前被 `.gitignore` 按文件名误排除，`SagittariusApp.kt` 三处引用它，导致任何 clone 均无法编译。已移除忽略规则并纳入版本控制。该文件仅含 AES-GCM + Android Keystore 加解密逻辑，不含任何密钥值。
- 干净副本验证：`git clone` 到临时目录后执行 `assembleDebug` + `testDebugUnitTest`，BUILD SUCCESSFUL（50 tasks，28 executed / 22 from cache），验证后临时目录已删除。
- 新增 `.github/workflows/android.yml`：push / PR 时用 JDK 17 执行 `assembleDebug` 与 `testDebugUnitTest`。
- 版本号：当前 `versionCode 62 / versionName 1.0`，与 `dist/sgr-release-1.0.apk` 实测一致；规则已写入 `BUILDING.md`。
- 未完成：本轮未 push，CI 尚未在 GitHub 远端实际执行过；lint 的 394 条 warning 未清理。


## 1.1 钠与维生素细分（2026-11-18）

- 数据库升级到 v10：`foods` 与 `diet_records` 新增钠及维生素 A/B1/B2/B3/C/E 字段，`nutrition_goals` 与 `nutrition_goal_overrides` 新增钠目标（默认 2000mg）。
- 迁移 9→10：新增列、清空 emoji 字段、按 `FoodCatalog` 回填预置食物、按克重回填历史饮食记录并快照图片。
- 预置 36 种食物补齐钠与 6 项维生素，来源为 `assets/cn_food.json` 对应条目；饺子与咖啡营养库无对应条目，使用估算值并在代码注释标明。
- 营养页新增钠进度条（参考上限 2000mg，超量警示色）与 6 项维生素细分展示（仅参考，不参与达标）；食物编辑、快捷录入、每日/特殊目标同步支持。
- 食材库、饮食记录、食材轮盘移除 emoji，改用 36 张 Pexels 真实图片（480×480，约 1.4MB），来源与作者记录在 `CREDITS.md`；无图自定义食物使用占位图。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 18 个测试类 / 47 用例 / 0 失败。
- Lint：0 error / 401 warning（新增为未使用资源等 warning，本轮未处理）。
- 迁移验证：用 Python sqlite3 按 `9.json` 建库、执行迁移 SQL，4 张表结构与 `10.json` 一致，200g 米饭记录的钠回填为 6.6mg，预置图片路径回填正确。
- 未完成：本机无设备/模拟器，未做真机 UI 验收；本轮未 push；APK 为 debug 签名，覆盖安装已发布的 release 版本会因签名不同失败。


## 1.1.1 营养圆环交互（2026-11-19）

- 钠由横向进度条改为圆形进度环；维生素 A/B1/B2/B3/C/E 改为 6 个圆形进度环。
- 圆环中心显示摄入值，下方显示名称与百分比；钠使用目标值（默认 2000mg），维生素使用成人参考摄入量（A 800μg、B1/B2 1.4mg、B3 14mg、C 100mg、E 14mg），仅参考、不计入达标。
- 触摸或鼠标移入圆环时，明细行显示「摄入量 / 参考值（百分比）」；钠超过目标时圆环与文字转为警示色。
- 移除 `SodiumBarView` 与 `VitaminGridView`，统一复用 `NutrientRingView`。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 18 个测试类 / 47 用例 / 0 失败；`lintDebug` 0 error / 403 warning。
- 未完成：本机无设备/模拟器，圆环触摸区域与文字排版未做真机验收。


## 1.1.2 主环并入钠与维生素（2026-11-19）

- 按反馈撤销「独立小环」方案：钠与维生素并入营养页主复合环，变为 8 个同心圆，由外向内为：钠、维生素、热量、碳水、脂肪、蛋白质、纤维、水。
- 主环不再绘制每圈数字，避免 8 圈时与中心文字重叠；百分比统一在下方图例展示（8 项：热量、蛋白质、碳水、脂肪、纤维、水、钠、维生素）。
- 触摸任意一圈，下方明细行显示该项目摄入量 / 目标 / 百分比；触摸维生素圈显示 A/B1/B2/B3/C/E 细分，触摸钠圈显示钠摄入与是否超限。
- 删除 `NutrientRingView` 与独立小环布局；图例高度 82dp → 112dp。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 18 个测试类 / 47 用例 / 0 失败；`lintDebug` 0 error / 401 warning。
- 未完成：本机无设备/模拟器，8 圈间距与触摸命中区域未做真机验收。


## 1.2 AI Agent UI 重写（2026-11-19）

- 删除桌宠：移除 AI 页 `petContainer`、`PetConfigSheet`、`TogglePetTool`、`UpdatePetConfigTool`、pet 资源与 DataStore key；预算悬浮胶囊、前台服务、通知与设置页开关保持不变。
- 入口改为 64×64dp 圆角星图按钮；用户素材经裁切、抠黑底、提亮、192×192 导出；点击后以按钮为原点做圆形扩散 + 边缘引力场粒子转场。
- AI 页重写为「对话 / 配置 / 终端」三选项卡；Host 持有共享 `SagittariusViewModel`，生成与审批不随 Tab 切换中断。
- 对话页：单行底部 dock（会话、联网、输入、推理、发送/停止）；会话胶囊从按钮弹性展开为卡片，支持切换、新建、长按多选、全选、批量删除；推理胶囊向上展开，内含推理强度与生动度两个竖向 4 档滑条。
- 配置页：服务商/模型 chips、Key 管理入口、分类记忆（人设/饮食习惯/记账偏好/其他）增删改与启停、人设/权限/上下文入口、主题与背景预留区。
- 记忆：Room v11 新增 `agent_memories`；系统提示词注入启用记忆（最多 50 条/1800 字）；新增 `remember_preference` 工具，用户说“记住…”时 AI 可写入。
- 终端页：常驻内存日志（环形 500 条、自动脱敏 Key）、工具目录（工具名/中文名/分类/说明）；两个卡片均可展开查看并导出 TXT / CSV。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 20 个测试类 / 53 用例 / 0 失败；`lintDebug` 0 error / 421 warning；Python 回放 10→11 迁移通过；APK `dist/sgr-debug-2.0-agent-ui.apk`（SHA256 `9C5D4EB6…0013`）。
- 未完成：本机无设备/模拟器，入口扩散、胶囊形变、竖向滑条与三 Tab 布局未做真机验收；未 push。


## 1.2.1 AI UI 二次重绘（2026-11-19）

- 按反馈移除顶部 Sagittarius 标题、返回按钮与三 Tab 按钮；改为 ViewPager2 左右滑动，仅保留非交互三点页码指示器。
- 入口特效改为从按钮扩散的实色覆盖层：先铺满整个屏幕，再让页面淡入，最后覆盖层淡出；粒子仍沿扩张边缘运动。
- 对话页删除服务商/模型状态栏；底部改为两行 dock（输入在上，会话/联网/推理/发送在下），按钮 44dp 以上并预留光晕空间。
- 入口按钮改为 72dp 内缩绘制（视觉 60dp），光晕限制在 view 留白内，修复边缘流光被裁切。
- 胶囊改用预算悬浮窗同款 `LiquidGlassView` + `SpringAnimation` + compact/expanded 状态切换，替换自绘 `MorphCapsuleView`。
- 终端页改为内嵌日志窗口与工具目录窗口，实时显示内容；保留 TXT/CSV 导出。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 20 个测试类 / 53 用例 / 0 失败；`lintDebug` 0 error / 441 warning；APK `dist/sgr-debug-2.1-agent-redraw.apk`（SHA256 `68680659…8D69`）。
- 未完成：本机无设备/模拟器，覆盖式转场、滑动切页、胶囊形变与内嵌窗口未做真机验收；未 push。


## 1.2.2 入口转场微调（2026-11-19）

- 移除扩散最外圈描边，只保留边缘粒子与墨迹本体。
- 扩散时长放慢：有粒子 660ms → 980ms，减少动画模式 340ms → 520ms。
- 铺满后延迟 60ms，页面与覆盖层同步 320ms 交叉淡入，避免先露出页面再消失的断层。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 20 类 / 53 用例 / 0 失败；APK 同上。


## 1.3 按钮/字体/胶囊/单位（2026-11-19）

- Agent 页按钮统一为黑色墨水圆角底、无流光/渐变描边；新增 `AgentTypography`，全页 TextView/EditText 统一 sans-serif 并收紧字距。Android 不内置 San Francisco，暂用系统 sans-serif 替代，若提供 SF Pro 字体文件可替换。
- 联网搜索按钮改为地球矢量图，推理按钮增加星芒矢量图标；两者均为墨水底。
- 修复胶囊收起变椭圆：`FloatingWindowService.collapse()` 先缩窗口，延迟 40ms 再切回 pill 圆角；`CapsuleOverlay.dismiss()` 不再在大尺寸下 `setExpanded(false)`，改为直接淡出展开卡片。
- 全 App 按钮去流光：`GradientActionButton` 删除旋转 SweepGradient 边框、外发光与呼吸填充，改为静态渐变填充 + 纯色 accent 边框；`GlassChipView` 选中态改低透明度纯色填充 + 纯色边框；隐私设置卡片关闭 flow 动画。
- 按钮颜色新增「透明」预设；透明时 `GradientActionButton`/`GlassChipView`/`GradientTabView` 回退到主题文字色，不绘制填充与边框。
- AI 新增 `set_food_unit` 工具：为已有食材设置自定义单位换算（如 1 根香蕉 = 100g），已注册、写入系统提示词与工具目录，补 2 条单测。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 435 warning；APK `dist/sgr-debug-2.2-agent-ink.apk`（SHA256 `5A7407CA…38DE`）。
- 未完成：本机无设备/模拟器，墨水按钮、字体观感、胶囊收起与透明按钮未做真机验收；未 push。


## 1.4 墨水页面与粒子胶囊（2026-11-19）

- 记饮食/记账页面的大圆形边缘改为跟随按钮颜色：`DietBubbleView` 边框、`BubbleAmountView` 外圈光晕与边框、`FoodTrackView` 轨道都读取 `ButtonAccentManager.current`。
- 对话页、配置页、终端页移除青色/渐变描边：聊天气泡改为墨水黑、文字固定浅色；配置/终端卡片改用黑墨底无描边；输入框、工具/选项 chips 同步墨水黑。
- 会话胶囊、推理胶囊改用 `InkCapsuleView` 纯黑圆角容器，删除 `LiquidGlassView` 玻璃与外发光；展开/收起由 `ParticleEdgeView` 沿胶囊圆角边缘喷出与入口同款粒子。
- 透明按钮色回退：发送箭头、联网图标、滑条、页码点在 accent 为透明时使用白色/主题浅色，避免消失。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 435 warning；APK `dist/sgr-debug-2.3-agent-ink2.apk`（SHA256 `EF66C979…F0CD`）。
- 未完成：本机无设备/模拟器，墨水泵面、胶囊粒子与饮食圆环颜色未做真机验收；未 push。


## 1.5 胶囊单实例与米白文字（2026-11-19）

- 会话胶囊与推理胶囊改为单一 `CapsuleOverlay` 实例；打开任一个前先 `dismissNow()` 清空旧遮罩，修复推理按钮同时唤起会话胶囊、遮罩残留卡住全屏的问题。
- 两个胶囊底部左侧新增「关闭」按钮；会话胶囊高度从 68% 降到 56%、宽度收窄，推理胶囊高度降到 52%，留出点击遮罩关闭的区域。
- Agent 页 accent 绿色文字统一改为米白 `#F3EBDD`：配置页服务商/模型/记忆选中项、会话列表当前项、聊天选项 chips、推理滑条数值与胶囊操作按钮。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 434 warning；APK `dist/sgr-debug-2.4-agent-fix.apk`（SHA256 `CF37FE19…AF1D`）。
- 未完成：本机无设备/模拟器，胶囊位置、关闭按钮命中与米白文字观感未做真机验收；未 push。


## 1.6 星空进度条与胶囊防重（2026-11-19）

- 胶囊复用修复：会话/推理共用一个 `CapsuleOverlay`，打开前 `dismissNow()` 清理旧覆盖层；连续点击不会再叠出多个胶囊。
- 选中态文字进一步统一为米白 `#F3EBDD`，覆盖服务商/模型、记忆分类、会话当前项、聊天选项、推理数值与胶囊操作按钮；绿色仅保留在滑条渐变/粒子等非文字元素。
- 进度条重绘（`VerticalLevelSliderView`）：轨道宽约 0.8cm、填充略窄；高度不变。填充由下往上浅蓝→蓝紫→紫色系渐变，实时按进度计算、`CLAMP` 无循环接缝；松手吸附后再回调最终档位。
- 最高档：轨道转深黑，填充切换为 `assets/starry_sky.mp4` 解码帧 + 程序化闪烁星点；视频帧异步加载，失败时仍用程序化星空。
- 将 `E:\AGI-sagittarius` 通过目录联接挂到 `E:\Sagittarius\AGI-sagittarius`，并加入 `.gitignore`，便于在工作区内访问素材与验证截图。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 436 warning；APK `dist/sgr-debug-2.5-star-slider.apk`（SHA256 `9928C751…A472`，17MB，含星空素材）。
- 未完成：本机无设备/模拟器，星空视频解码、渐变与胶囊连续点击未真机验收；未 push。


## 1.7 墨水黑主题与 Agent 固定主题（2026-11-19）

- 黑色主题背景 `bg_screen_black` 从黑底 + 青绿/蓝/粉三团径向光效改为纯墨 `#0B1220`。
- 黑色主题调色板统一：文字米白/浅灰、accent 米白、按钮与标题渐变改为墨色、卡片描边降为白色微透明、flow/soft 改为墨色低透明。
- 黑色主题下 `ButtonAccentManager.current` 返回墨水黑样式（文字米白），因此底部 tabs、主要按钮、chips、模式切换等统一为墨水黑无光效；`GradientTabView`/`GradientActionButton`/`GlassChipView` 均做了 ink 分支。
- 入口涟漪粒子改为米白/白/浅蓝灰固定色，不再使用绿色 accent。
- Agent 页新增 `Theme.Sagittarius.Agent`（父 Black），`SagittariusActivity` 强制使用该主题，`followsThemeChanges=false`、禁用 Glass 模糊；即使全局选择浅色，Agent 页仍为墨水黑。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 440 warning；APK `dist/sgr-debug-2.6-ink-theme.apk`（SHA256 `6AA1A02A…6067`，17MB）。
- 未完成：本机无设备/模拟器，黑色主题全局观感、浅色主题下 Agent 固定墨水黑未真机验收；未 push。


## 1.8 滑条与纯黑白粒子（2026-11-19）

- 推理胶囊高度增加约 1.5cm（最大 474dp），删除「推理与生动度」标题和「完成」按钮，仅保留底部「关闭」。
- 关闭/切换胶囊前调用滑条 `commit()`，立即吸附档位并保存当前配置，不再等待收起动画结束。
- 松手吸附改为 150ms Decelerate 动画，拖动过程连续更新、无档位突变；填充色改为 HSV 连续插值（浅蓝→蓝紫→紫→品红），无硬阈值跳色。
- 最高档星空：视频帧做饱和度归零过滤，消除蓝色星点；程序化星点只使用白色，并叠加双层柔光；不再出现蓝色星。
- 胶囊展开/收起粒子与入口涟漪粒子统一为黑/白两色，删除青蓝与米白彩色粒子。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 441 warning；APK `dist/sgr-debug-2.7-ink-final.apk`（SHA256 `490EBE34…9313`，18MB）。
- 未完成：本机无设备/模拟器，滑条吸附手感、星空视频帧与黑白粒子未真机验收；未 push。


## 1.9 米白液体边框与毛玻璃修复（2026-11-19）

- 黑色主题与 Agent 页按钮删除剩余高光/光感：`GradientActionButton` 在 ink 模式只画纯墨填充，不画顶部高光；Agent 入口按钮改为纯墨底 + 米白描边。
- 新增 `bg_agent_ink_border`（墨底 + 1.2dp 米白描边 + 24dp 圆角），Agent 对话 dock、配置/终端按钮、chips、清空上下文按钮统一使用。
- 黑色主题 chips、输入框、悬浮按钮描边改为米白色系；`GradientActionButton`/`GlassChipView` 在 ink 模式下边框为米白。
- 毛玻璃主题修复：`values-v31` 不再使用 `windowIsTranslucent/windowShowWallpaper`，改为不透明玻璃背景；`ThemedActivity` 默认关闭 Glass 背景模糊，避免部分机型反复重绘/闪烁。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 442 warning；APK `dist/sgr-debug-2.8-cream-border.apk`（SHA256 `773F3ACA…ED14`）。
- 未完成：本机无设备/模拟器，毛玻璃稳定性与米白边框观感未真机验收；未 push。


## 2.0 气泡描边、入口渐变与 span 颜色（2026-11-19）

- 聊天气泡新增米白描边：用户气泡 `#99F3EBDD`、管家气泡 `#CCF3EBDD`，1.2dp，与 Agent 按钮的液体边框一致。
- 入口转场恢复可见渐变：扩散圆由中心亮蓝 `#33507E` → `#1B2C4C` → 墨黑收束，外缘柔和透明，不再是一块近似纯黑；同时首页启动 Agent 时窗口 220ms 淡入，消除点击后瞬间黑屏。
- 黑色主题（及深色墨水按钮）下，饮食球 `DietBubbleView` 与记账球 `BubbleAmountView` 改为米白 `#CCF3EBDD` 描边，删除外发光、白色高光与顶部高光弧线；浅色主题仍沿用按钮强调色。
- `RichTextParser` 新增 HTML 写法支持：`<span style="color:cyan">…</span>`、`<font color="#FF0000">…</font>`，颜色支持命名色、`#RGB/#RRGGBB/#AARRGGBB` 与 `rgb(r,g,b)`；无颜色的 span 标签直接丢弃，不再把源码显示在气泡里。
- 毛玻璃主题：删除 `drawable-v31/bg_screen_glass.xml`（半透明底色），v31 统一回落到不透明 `bg_screen_glass`；`windowIsTranslucent/windowShowWallpaper/backgroundDimEnabled=false` 下沉到 `Glass.Base`，导航栏改不透明 `#0A0F1D`。此前不透明窗口里绘制半透明底色，会在 HyperOS 上反复重绘，是闪烁的根因。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 446 warning；APK `dist/sgr-debug-2.9-ai-polish.apk`（SHA256 `42660944…46BD`，17MB）。
- 未完成：本机无设备/模拟器，气泡描边、入口渐变与 Redmi K80 上的毛玻璃稳定性未真机验收；未 push。


## 2.1 真磨砂、星芒滑条与奶油主题（2026-11-19）

- 磨砂主题：改为半透明窗口 + `windowShowWallpaper`，Activity 侧对 Android 12+ 设置 `FLAG_BLUR_BEHIND` 与 42dp 背景模糊，窗口底色仅留 `#590A0F1D` 薄雾，真正透出桌面；主题名「毛玻璃」改为「磨砂」。桌面壁纸不上传、不读取（Android 13+ 第三方应用已无法读取壁纸位图），由系统直接绘制并模糊。API 26–30 无系统背景模糊，仍为透明薄雾玻璃。
- 联网按钮：开启时米白半透明填充 + 米白描边 + 右上角状态点 + 图标米白 + 180ms 回弹；关闭时墨底细描边、图标灰色、整体 82% 透明度。
- 推理/生动度滑条最高档重绘：26 颗白色四角星芒粒子，持续缓慢平移与旋转，按正弦呼吸淡入淡出，叠加两层白色柔光与放大星芒，形成荧光感；动画周期 9s，减少动画模式自动停止。
- 新增奶油主题（`Theme.Sagittarius.Ins`）：奶油底 + 网上抓取的暖色渐变薄雾（Pexels #7630065，Eva Bronzini，已裁切/模糊/混色，署名见 `CREDITS.md`），焦糖强调色、半透明白卡、暖色细描边、奶油/玫瑰/雾紫按钮渐变；`ThemeMode` 新增 `INS`，设置页、AI `update_theme` 工具与错误文案同步支持 `ins`。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 446 warning；APK `dist/sgr-debug-3.0-themes.apk`（SHA256 `12D7A457…0E55`，17MB）。
- 未完成：本机无设备/模拟器，真磨砂在 Redmi K80/HyperOS 上是否仍抽搐、奶油主题观感与星芒滑条均未真机验收；未 push。


## 2.2 终端代码框、星芒下落与新会话 logo（2026-11-20）

- 终端页：运行日志与工具清单套上代码框（`bg_agent_code_box`：更深的墨底 + 细米白描边 + 10dp 内边距），日志强制等宽字体；工具行改为 `▸ tool_name` + `// 中文名 · 分类 · 只读` + 缩进说明，整体按代码文件排版。
- 进度条星芒：删除两层透明圆形光圈，只保留星形荧光轮廓；所有粒子在原有水平漂移、旋转、呼吸之外增加持续向下移动的总趋势。每颗星拥有独立寿命（4.5–9s），淡入、下落、淡出后从顶部独立重生，不再整屏周期性重置；时间源改为单调递增的 `SystemClock` 累计值。
- 顶部时间胶囊色板新增「焦糖」（`#C58A66 → #B87A8E`）。
- AI 新会话：空态显示 `AgentLogoView`——按 `sag.png` 提取 21 个星点与 25 条连线，星点用进度条同款四角星芒，连线为双层白色发光细线并呼吸减淡；星点小范围漂移/自转，连线实时跟随。空态面板改为不透明墨底，避免欢迎气泡与 logo 重叠。
- 新增偏好 `new_session_on_enter`（默认开启）：每次进入 AI 页自动新建会话；设置页「可见性与权限」新增「每次进入新建会话」开关，关闭后回到上次会话。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 446 warning；APK `dist/sgr-debug-3.1-terminal-logo.apk`（SHA256 `D08D113B…2DF9`，17MB）。
- 未完成：本机无设备/模拟器，代码框排版、星芒下落重生、logo 动画与新会话开关未真机验收；未 push。


## 2.3 入口涟漪透出原页面（2026-11-22）

- `Theme.Sagittarius.Agent` 改为半透明窗口：`windowIsTranslucent=true`、`windowBackground=transparent`、`windowShowWallpaper=false`、`windowDisablePreview=true`，不再用主题不透明底挡住首页。
- AI 页 `contentRoot` 增加 `bg_screen_black` 不透明墨底；初始 `alpha=0`，涟漪外的区域保持透明，因此扩散时能看到首页原选项卡（天气等），铺满后页面淡入再淡出覆盖层，形成“从按钮流出整页并覆盖”的效果。
- 首页退出转场 `agent_fade_hold` 时长 220ms → 1400ms，覆盖涟漪扩散（980ms）+ 交叉淡入/淡出（约 380ms），保证整段动画期间首页仍在底层可见。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 446 warning；APK `dist/sgr-debug-3.2-ripple.apk`（SHA256 `3F53E7E3…53B3`，17MB）。
- 未完成：本机无设备/模拟器，透出首页的转场效果未真机验收；未 push。


## 2.4 圆形揭开首页（2026-11-22）

- 入口转场从“深色圆盘铺满再淡入页面”改为真正的圆形揭开：AI 页 `contentRoot` 用 `ViewOutlineProvider` + `clipToOutline` 按按钮圆心裁剪，扩散半径通过 `AgentEntryRevealView.setOnReveal` 每帧回传；圆内是 AI 页面本体，圆外窗口保持透明，直接露出首页当前选项卡（天气等）。
- 首帧前同步设置裁剪，避免闪出整页；铺满后关闭 `clipToOutline` 恢复整页，粒子覆盖层在 380ms 内淡出。
- Agent 主题保持半透明窗口（`windowIsTranslucent=true`、透明 window 背景），`contentRoot` 使用 `bg_screen_black` 不透明墨底；首页退场 `agent_fade_hold` 保持 1400ms，覆盖 980ms 扩散 + 淡出。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 21 个测试类 / 55 用例 / 0 失败；`lintDebug` 0 error / 444 warning；APK `dist/sgr-debug-3.3-reveal.apk`（SHA256 `0990C9AF…EB4F`，17MB）。
- 未完成：本机无设备/模拟器，圆形裁剪边缘是否锯齿、首页是否完整透出未真机验收；未 push。


## 2.5 特效标签、食材配图、附件与查询增强（2026-11-24）

- 富文本：新增 `<rainbow>`、`<b>/<strong>`、`<i>/<em>`、`<u>`、`<big>/<small>`、`<br>`；`<span style="font-weight:bold|font-style:italic|text-decoration:underline">` 在无颜色时也能生效；非流式最终文本会补齐漏写的闭合标签，不再出现“标签写了但没效果”。
- 食材配图：新增 `set_food_image` 工具。可传图片 URL，或按食物名/关键词从 Wikimedia Commons 搜索 900px 缩略图；下载后压缩为 JPEG 存入应用私有目录并写回 `foods.imageUri`，工具结果会带作者与许可信息。食材不存在时会提示先 `add_food_to_library`。同步移除思考提示里的“为了省钱钱，不会爬图图…”。
- 聊天附件：dock 新增回形针按钮（最多 3 个），支持图片、TXT/MD/CSV/TSV/JSON/XML、DOCX、XLSX；文本/表格提取纯文本（每条上限 12000 字），图片压到 1024px 后转 data URL。文本附件以 `[附件]` 追加到本轮消息；图片仅在模型名匹配视觉模型（vision/vl/4v/gpt-4o/gemini/claude/qwen-vl/llava 等）时以 OpenAI 兼容的 `image_url` 发送，否则提示换视觉模型。用户气泡显示附件名，历史回放时剥离附件正文，避免撑满气泡。
- 查询增强：`query_data` 支持 expense/budget/diet/nutrition/overview、日期相对词（today/yesterday/this_week/this_month/last_month）、category/keyword/meal 筛选、sort、limit、include_items；返回分类/按日/按餐次/食物排行、日均、最大单笔、钠与维生素 A/B1/B2/B3/C/E。系统提示词新增查询口径与能力地图；旧 `query_app_data` 不再注册，避免选错工具。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` **22 个测试类 / 58 用例 / 0 失败**（新增 QueryDataTool 3 例）；`lintDebug` 0 error / 446 warning；APK `dist/sgr-debug-3.4-tools.apk`（SHA256 `0FB01F2D…86D5`，17MB）。
- 未完成：本机无设备/模拟器，附件选择/解析、视觉模型读图、Wikimedia 搜图、富文本特效与新查询口径未做真机与真实 API 联调；PDF 仅提示不支持；未 push。


## 2.6 图片附件不再按模型名猜测（2026-11-24）

- 修正 2.5 的保守策略：不再用模型名（vision/vl/...）判断能否读图，图片一律以 OpenAI 兼容的 `image_url` 随本轮消息发送，由接口自己决定是否支持。
- `AgentEngine` 增加降级：若接口返回 400/解析失败且本轮带图，先剥离历史中的图片 data URL（并补一条“接口不接受图片输入”的文字说明）重试一次；仍失败才走原有的工具降级/报错。
- 历史中只保留本轮图片；下一轮发送前自动清除旧图片 payload，避免每轮重复上传 base64。
- 提示词改为“图片会随本轮消息发送，接口不接受时系统自动降级”，不再宣称 DeepSeek 不能读图。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 22 个测试类 / 58 用例 / 0 失败；`lintDebug` 0 error / 446 warning；APK `dist/sgr-debug-3.5-vision.apk`（SHA256 `CA304607…844B`，17MB）。
- 未完成：本机无设备/模拟器，真实接口的图片请求与 400 降级路径未联调；未 push。


## 2.7 执行过程去重、附件配图与余额角标（2026-11-24）

- 修复执行过程重复：移除了 `OkHttpLlmClient` 在 content 为空时把 reasoning 当作正文发送的兜底；`AgentEngine` 再对“content 与 reasoning 完全相同/以 reasoning 开头”的响应做去重。执行过程只留在默认折叠的“执行过程”里，正文只显示最终输出；只有思考没有正文时正文区留空，不再把思考塞进对话。
- 用户上传的图片可直接设为食材图片：聊天图片压缩后会同时写入 cache 路径，VM 把本轮图片路径通过 `attachmentImagesProvider` 传给 `set_food_image`；工具新增 `use_attachment` 与 `attachment_index`，默认允许覆盖已有图片（`overwrite=true`），提示词同步说明。
- 每条 AI 回复右上角新增绿色余额角标：显示 `¥x.xx`，点击调用 `refreshBalance()` 刷新；非 DeepSeek 服务商不显示。角标随余额 Flow 更新，不需要重建列表。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 22 个测试类 / 58 用例 / 0 失败；`lintDebug` 0 error / 447 warning；APK `dist/sgr-debug-3.6-reasoning-balance.apk`（SHA256 `CCABCB46…867C`，17MB）。
- 未完成：本机无设备/模拟器，执行过程去重、聊天图片覆盖食材图与余额角标未真机验收；未 push。


## 2.8 上一轮图片仍可用于配图（2026-11-24）

- 聊天图片路径不再在每轮结束时清空，而是保留到切换/删除会话或下一张图片上传；用户下一轮说“把刚才那张图设成洋葱的图片”时，`set_food_image(use_attachment=true)` 仍可用。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 22 个测试类 / 58 用例 / 0 失败；`lintDebug` 0 error / 447 warning；APK `dist/sgr-debug-3.7-fixes.apk`（SHA256 `CB92835A…340D`，17MB）。
- 未完成：本机无设备/模拟器，跨轮附件配图未真机验收；未 push。


## 3.0 1.1.0 发布（2026-11-24）

- 版本：`versionCode = 63`、`versionName = "1.1.0"`；包名不变 `com.dsha.sagittarius`。
- Release APK 使用 1.0 同一证书签名（证书 SHA-256 `66EB47B2D8A7A17A1FAC4B2EA957FF15ACCC0D3C282DADDA3DE3363D484412E4`），已用 `apksigner verify` 确认 v2 签名通过，可直接覆盖升级 1.0。
- 本地 release 签名读取 `keystore/keystore.properties`（已 gitignore）；CI 没有该文件时只构建 debug，不会产出未签名 release。
- 产物：`dist/sgr-release-1.1.0.apk`，大小约 14.7MB，SHA-256 `CEBB8B86F8F52483B3F849245105D30E451294F12F7A85EEB707BBC16AE4E5FB`。
- 本次发布包含：四套主题（黑/浅/磨砂/奶油）、圆形揭开转场、星座新会话 logo、终端代码框、星芒滑条、富文本 rainbow/b/i/u 等、AI 食物配图、聊天附件（图片/CSV/Word/Excel）、余额角标、query_data 查询增强与执行过程去重。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 22 个测试类 / 58 用例 / 0 失败；`lintDebug` 0 error / 447 warning；`assembleRelease` BUILD SUCCESSFUL。
- 未完成：本机无设备/模拟器，1.1 覆盖安装与新增视觉效果未真机验收。


## 3.1 1.1.1 修复（2026-11-26）

- 食材营养写入：`add_food_to_library` 与 `manage_food_library` 的 schema/写入扩展到热量、碳水、蛋白、脂肪、纤维、水分、钠、维生素总量、维生素 A/B1/B2/B3/C/E；新增/更新后返回 `missing_nutrition`，提示 AI 继续查或估算补齐。`manage_food_library` 新增 `search` / `list`，旧 `query_app_data` 仍不注册。
- 食材录入步骤写入系统提示词：先查用户食材库 → 内置中国食物库 → 联网搜索/估算 → 写入尽可能全的营养 → 完成后先问是否配图，同意后再 `set_food_image`（支持聊天图片附件与覆盖旧图）。
- 修复工具完成后偶发“请求错误”：DeepSeek 思考模式下带 `tool_calls` 的 assistant 消息现在总会带回 `reasoning_content`（无推理时补空字符串），`content` 也补空字符串；工具结果写入历史前截断到 16k 字；关闭工具重试时把 tool_calls/tool 消息折叠成普通文本；工具已成功但模型无补充文字时不再报错，直接结束。
- 版本：`versionCode = 64`、`versionName = 1.1.1`；release APK 沿用 1.0 证书，可直接覆盖 1.1.0。
- 验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` **24 个测试类 / 60 用例 / 0 失败**（新增 LlmJson 工具消息、食材全营养字段测试）；`lintDebug` 0 error / 447 warning；`assembleRelease` BUILD SUCCESSFUL；APK `dist/sgr-release-1.1.1.apk`（SHA256 `00A99CDB…483D`，14.7MB）。
- 未推送：1.1.1 尚未 push / 发 Release，等待确认。
