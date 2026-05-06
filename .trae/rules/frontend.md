## 一、技术栈（强制版本）
- **Vue**: 3.5.30
- **UI 框架**: Element Plus 2.13.2+
- **路由**: Vue Router 5.0.3+
- **状态管理**: Pinia 3.0.4+
- **HTTP 客户端**: Axios 1.16.0+
- **构建工具**: Vite 8.0.10+
- **CSS 预处理器**: Sass 1.99.0+（使用 `lang="scss"`）
- **图标**: @element-plus/icons-vue 2.3.2+
- **代码风格**: ESLint + Prettier（必须遵循）
- **包管理**: pnpm（优先）
- **@vitejs/plugin-vue**: 6.0.0+
## 二、开发规范
1. 组件规范
- 统一使用 **Vue3 组合式API + `组件**单一职责**，一个组件只做一件事
- 公共组件放在 `/src/components/`，页面组件放在 `/src/views/`
- 组件名使用 **大驼峰（UpperCamelCase）**，如 `UserList.vue`
- 模板必须有**一个根节点**，可使用 `禁止在模板中编写复杂逻辑，逻辑必须抽到 `setup` 中
- 基础类型用 `ref`，对象/数组用 `reactive`
- 优先使用：`ref` / `reactive` / `computed` / `watch`
- 禁止使用 `this`，禁止使用 Vue2 写法
- 解构 props 必须用 `toRefs` / `toRef` 保持响应式
3. 代码风格与格式
- 变量/方法名：**小驼峰 camelCase**
- 常量：**全大写 + 下划线**
- 每行代码不超过 120 字符
- 必须使用**单引号**
- 语句末尾**不加分号**
- 缩进使用 **2 个空格**
4. 类型与注释（强制）
- 复杂数据、函数参数、返回值必须使用 **JSDoc 注释**
- 禁止无意义变量名（如 `a`/`b`/`temp`）
- 关键业务逻辑必须写注释
## 三、API 请求规范
- 统一使用 **封装后的 Axios**，禁止直接使用 axios.get()
- 请求拦截器：自动携带 Token、设置请求头
- 响应拦截器：统一处理 401/403/500/网络错误
- API 按**业务模块**拆分文件，如 `user.js` / `order.js`
- 请求方法统一命名：`getXxx` / `postXxx` / `putXxx` / `deleteXxx`
- 所有请求必须做 **try/catch** 异常处理
## 四、路由规范
- 路由使用 **按需加载（懒加载）**
- 使用 `createRouter` / `createWebHistory`
- 路由守卫：统一校验登录状态、页面权限
- `meta` 固定字段：`title` / `requiresAuth` / `roles`
- 路由路径使用小写 + 横线：`/user-list`
## 五、Pinia 状态管理规范
- 按模块定义 store：`useUserStore` / `useAppStore`
- 统一使用**组合式 Store** 写法
- 数据放 state，方法放 actions，计算属性放 getters
- 禁止直接修改 state，必须通过 actions 修改
- 异步请求放在 actions 中
## 六、Element Plus 使用规范
- 组件使用 **自动导入**，禁止全局大量注册组件
- 表单校验使用内置规则，统一写在 `弹框、通知使用：`ElMessage` / `ElMessageBox` / `ElNotification`
- 图标直接从 `@element-plus/icons-vue` 导入使用
- 禁止使用 Vue2 语法
- 禁止在 `禁止硬编码固定文本、路径、常量
- 禁止不做异常处理的网络请求
- 禁止组件代码超过 300 行（必须拆分）
- 禁止使用 onLoad、onShow 等非 Vue 生命周期钩子
## 七、编译与语法校验规则
- 所有功能代码编写完成后，必须保证代码可正常编译、无语法报错