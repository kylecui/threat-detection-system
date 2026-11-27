# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: login-key-repeat.spec.ts >> Password key repeat (ordinary, en-US) >> Space holds toggle once, release safely and do not submit valid forms
- Location: e2e\login-key-repeat.spec.ts:94:11

# Error details

```
Error: expect(locator).toHaveAttribute(expected) failed

Locator:  getByLabel('Password', { exact: true })
Expected: "text"
Received: "password"
Timeout:  5000ms

Call log:
  - Expect "toHaveAttribute" with timeout 5000ms
  - waiting for getByLabel('Password', { exact: true })
    2 × locator resolved to <input type="password" value="Valid 019" id="login_password" aria-required="true" aria-label="Password" autocomplete="current-password" placeholder="Please enter password" class="ant-input ant-input-lg css-dev-only-do-not-override-i8la2z"/>
      - unexpected value "password"
    7 × locator resolved to <input type="password" id="login_password" aria-required="true" aria-label="Password" autocomplete="current-password" placeholder="Please enter password" class="ant-input ant-input-lg css-dev-only-do-not-override-i8la2z"/>
      - unexpected value "password"

```

# Page snapshot

```yaml
- main [ref=e3]:
  - region "Login" [ref=e4]:
    - generic [ref=e5]:
      - img [ref=e7]:
        - img [ref=e8]
      - generic [ref=e10]:
        - heading "Threat Detection System" [level=1] [ref=e11]
        - paragraph [ref=e12]: Cloud-Native Threat Detection Platform
    - generic [ref=e15]:
      - heading "Login" [level=2] [ref=e17]
      - generic [ref=e18]:
        - generic [ref=e20]:
          - generic "Username" [ref=e22]: "* Username"
          - generic [ref=e26]:
            - img "user" [ref=e28]:
              - img [ref=e29]
            - textbox "Username" [ref=e31]:
              - /placeholder: Please enter username
              - text: admin_019
        - generic [ref=e33]:
          - generic "Password" [ref=e35]: "* Password"
          - generic [ref=e39]:
            - img "lock" [ref=e41]:
              - img [ref=e42]
            - textbox "Password" [ref=e44]:
              - /placeholder: Please enter password
              - text: Valid 019
            - button "Show password" [active] [ref=e46] [cursor=pointer]:
              - img [ref=e47]:
                - img [ref=e48]
        - button "Login" [ref=e56] [cursor=pointer]:
          - generic [ref=e57]: Login
```

# Test source

```ts
  68  |         pressed: button.getAttribute("aria-pressed"),
  69  |         name: button.getAttribute("aria-label"),
  70  |         focused: document.activeElement === button,
  71  |         width: rect.width,
  72  |         height: rect.height,
  73  |       };
  74  |     }),
  75  |   );
  76  | }
  77  | 
  78  | function toggleFor(input: Locator) {
  79  |   return input
  80  |     .locator("xpath=..", {})
  81  |     .locator("button.ant-input-password-icon");
  82  | }
  83  | 
  84  | for (const mode of MODES) {
  85  |   test.describe(`Password key repeat (${mode.name}, ${mode.language})`, () => {
  86  |     test.use({
  87  |       viewport: mode.mobile
  88  |         ? { width: 390, height: 600 }
  89  |         : { width: 1440, height: 900 },
  90  |       hasTouch: mode.mobile,
  91  |     });
  92  | 
  93  |     for (const key of ["Space", "Enter"] as const) {
  94  |       test(`${key} holds toggle once, release safely and do not submit valid forms`, async ({
  95  |         page,
  96  |       }, testInfo) => {
  97  |         await stubApiAvailability(page);
  98  |         await page.addInitScript((language) => {
  99  |           localStorage.setItem("tds-lang", language);
  100 |         }, mode.language);
  101 |         await page.route("**/api/v1/auth/bootstrap/status", (route) =>
  102 |           route.fulfill({ json: { bootstrapRequired: mode.bootstrap } }),
  103 |         );
  104 |         const posts = { bootstrap: 0, login: 0 };
  105 |         for (const endpoint of ["bootstrap", "login"] as const) {
  106 |           await page.route(`**/api/v1/auth/${endpoint}`, (route) => {
  107 |             posts[endpoint] += 1;
  108 |             return route.fulfill({
  109 |               status: 400,
  110 |               json: { message: "Unexpected visibility-control submission" },
  111 |             });
  112 |           });
  113 |         }
  114 |         await gotoReady(page, "/login");
  115 |         await page
  116 |           .getByLabel(tr(mode.language, "login.username"), { exact: true })
  117 |           .fill("admin_019");
  118 |         const fields: ToggleField[] = mode.bootstrap
  119 |           ? ["password", "confirmPassword"]
  120 |           : ["password"];
  121 |         for (const field of fields) {
  122 |           await page
  123 |             .getByLabel(tr(mode.language, `login.${field}`), { exact: true })
  124 |             .fill(PASSWORD);
  125 |         }
  126 |         if (mode.bootstrap) {
  127 |           await page
  128 |             .getByLabel(tr(mode.language, "login.displayName"), { exact: true })
  129 |             .fill("Key repeat regression");
  130 |         }
  131 |         await installRecorder(page);
  132 |         const snapshots: Array<{
  133 |           stage: string;
  134 |           fields: Awaited<ReturnType<typeof fieldsState>>;
  135 |           posts: typeof posts;
  136 |         }> = [];
  137 | 
  138 |         try {
  139 |           for (const field of fields) {
  140 |             const input = page.getByLabel(tr(mode.language, `login.${field}`), {
  141 |               exact: true,
  142 |             });
  143 |             const button = toggleFor(input);
  144 |             await input.focus();
  145 |             await page.keyboard.press("Tab");
  146 |             await expect(button).toBeFocused();
  147 |             await expect(button).toHaveAttribute("type", "button");
  148 |             await expect(button).toBeInViewport();
  149 |             const outline = await button.evaluate((element) => {
  150 |               const style = getComputedStyle(element);
  151 |               return { style: style.outlineStyle, width: style.outlineWidth };
  152 |             });
  153 |             expect(outline.style).not.toBe("none");
  154 |             expect(parseFloat(outline.width)).toBeGreaterThanOrEqual(2);
  155 |             const initial = await fieldsState(page);
  156 | 
  157 |             const assertState = async (
  158 |               stage: string,
  159 |               visible: boolean,
  160 |               keyboardFocus = true,
  161 |             ) => {
  162 |               const state = await fieldsState(page);
  163 |               snapshots.push({
  164 |                 stage: `${field}:${stage}`,
  165 |                 fields: state,
  166 |                 posts: { ...posts },
  167 |               });
> 168 |               await expect(input).toHaveAttribute(
      |                                   ^ Error: expect(locator).toHaveAttribute(expected) failed
  169 |                 "type",
  170 |                 visible ? "text" : "password",
  171 |               );
  172 |               await expect(input).toHaveValue(PASSWORD);
  173 |               await expect(button).toHaveAttribute(
  174 |                 "aria-pressed",
  175 |                 String(visible),
  176 |               );
  177 |               await expect(button).toHaveAccessibleName(
  178 |                 tr(
  179 |                   mode.language,
  180 |                   visible ? "login.hidePassword" : "login.showPassword",
  181 |                 ),
  182 |               );
  183 |               if (keyboardFocus) await expect(button).toBeFocused();
  184 |               for (const item of state) {
  185 |                 expect(item.value).toBe(PASSWORD);
  186 |                 expect(item.width).toBeGreaterThanOrEqual(44);
  187 |                 expect(item.height).toBeGreaterThanOrEqual(44);
  188 |                 if (item.id !== `login_${field}`) {
  189 |                   expect(item).toMatchObject(
  190 |                     initial.find((previous) => previous.id === item.id)!,
  191 |                   );
  192 |                 }
  193 |               }
  194 |               expect(posts).toEqual({ bootstrap: 0, login: 0 });
  195 |             };
  196 | 
  197 |             // Each cycle has one initial keydown, three OS-style repeats and keyup.
  198 |             // The second cycle starts visible, and must still toggle only once.
  199 |             for (const visible of [true, false]) {
  200 |               const before = (await recordedKeys(page)).length;
  201 |               try {
  202 |                 await page.keyboard.down(key);
  203 |                 await assertState(`${key}:initial-${visible}`, visible);
  204 |                 for (let repeat = 1; repeat <= 3; repeat += 1) {
  205 |                   await page.keyboard.down(key);
  206 |                   await assertState(
  207 |                     `${key}:repeat-${repeat}-${visible}`,
  208 |                     visible,
  209 |                   );
  210 |                 }
  211 |               } finally {
  212 |                 await page.keyboard.up(key);
  213 |               }
  214 |               await assertState(`${key}:released-${visible}`, visible);
  215 |               expect((await recordedKeys(page)).slice(before)).toEqual([
  216 |                 {
  217 |                   type: "keydown",
  218 |                   key: key === "Space" ? " " : "Enter",
  219 |                   repeat: false,
  220 |                   field: `login_${field}`,
  221 |                 },
  222 |                 ...Array.from({ length: 3 }, () => ({
  223 |                   type: "keydown",
  224 |                   key: key === "Space" ? " " : "Enter",
  225 |                   repeat: true,
  226 |                   field: `login_${field}`,
  227 |                 })),
  228 |                 {
  229 |                   type: "keyup",
  230 |                   key: key === "Space" ? " " : "Enter",
  231 |                   repeat: false,
  232 |                   field: `login_${field}`,
  233 |                 },
  234 |               ]);
  235 |             }
  236 | 
  237 |             await page.keyboard.press(key);
  238 |             await assertState(`${key}:independent-press-1`, true);
  239 |             await page.keyboard.press(key);
  240 |             await assertState(`${key}:independent-press-2`, false);
  241 |             if (mode.mobile) await button.tap();
  242 |             else await button.click();
  243 |             await assertState("pointer-click-1", true, false);
  244 |             if (mode.mobile) await button.tap();
  245 |             else await button.click();
  246 |             await assertState("pointer-click-2", false, false);
  247 |           }
  248 |           await expect(page.locator(".ant-form-item-has-error")).toHaveCount(0);
  249 |           await expect(page).toHaveURL(/\/login$/);
  250 |           expect(posts).toEqual({ bootstrap: 0, login: 0 });
  251 |         } finally {
  252 |           const events = await recordedKeys(page);
  253 |           await testInfo.attach("password-key-repeat", {
  254 |             contentType: "application/json",
  255 |             body: JSON.stringify(
  256 |               { mode, key, events, snapshots, posts },
  257 |               null,
  258 |               2,
  259 |             ),
  260 |           });
  261 |           await page.locator("form").evaluate((element) => {
  262 |             const form = element as RecordedForm;
  263 |             form.removeRepeatRecorder?.();
  264 |             delete form.repeatEvents;
  265 |             delete form.removeRepeatRecorder;
  266 |           });
  267 |         }
  268 |       });
```