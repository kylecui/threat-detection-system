# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: login-contrast.spec.ts >> login primary text contrast: zh-CN, light, ordinary
- Location: e2e\login-contrast.spec.ts:155:11

# Error details

```
Error: hover: rgb(255, 255, 255) on rgb(35, 146, 168)

expect(received).toBeGreaterThanOrEqual(expected)

Expected: >= 4.5
Received:    3.653159082742227
```

# Page snapshot

```yaml
- main [ref=e3]:
  - region "登录" [ref=e4]:
    - generic [ref=e5]:
      - img [ref=e7]:
        - img [ref=e8]
      - generic [ref=e10]:
        - heading "威胁检测系统" [level=1] [ref=e11]
        - paragraph [ref=e12]: 云原生威胁检测平台
    - generic [ref=e15]:
      - heading "登录" [level=2] [ref=e17]
      - generic [ref=e18]:
        - generic [ref=e20]:
          - generic "用户名" [ref=e22]: "* 用户名"
          - generic [ref=e26]:
            - img "user" [ref=e28]:
              - img [ref=e29]
            - textbox "用户名" [ref=e31]:
              - /placeholder: 请输入用户名
        - generic [ref=e33]:
          - generic "密码" [ref=e35]: "* 密码"
          - generic [ref=e39]:
            - img "lock" [ref=e41]:
              - img [ref=e42]
            - textbox "密码" [active] [ref=e44]:
              - /placeholder: 请输入密码
            - button "显示密码" [ref=e46] [cursor=pointer]:
              - img [ref=e47]:
                - img [ref=e48]
        - button "登录" [ref=e56] [cursor=pointer]:
          - generic [ref=e57]: 登 录
```

# Test source

```ts
  178 |         await gotoReady(page, "/login");
  179 |         const button = page.locator(".tds-login__submit button[type='submit']");
  180 |         const label = tr(
  181 |           language,
  182 |           bootstrap ? "login.createAdmin" : "login.login",
  183 |         );
  184 |         await expect(button).toBeEnabled();
  185 |         await expect(button).not.toHaveClass(/ant-btn-loading/);
  186 |         await expect(button).toHaveAccessibleName(label);
  187 |         const samples: ButtonSample[] = [];
  188 | 
  189 |         await page.mouse.move(1, 1);
  190 |         samples.push(await sampleButton(button, "normal"));
  191 |         await button.hover();
  192 |         samples.push(await sampleButton(button, "hover"));
  193 |         const evidenceName = `${language}-${preference}-${bootstrap ? "bootstrap" : "ordinary"}`;
  194 |         const evidenceDir = process.env.TDS_LOGIN_EVIDENCE_DIR;
  195 |         if (evidenceDir) {
  196 |           await mkdir(evidenceDir, { recursive: true });
  197 |           await page.screenshot({
  198 |             path: path.join(evidenceDir, `${evidenceName}-hover.png`),
  199 |             fullPage: true,
  200 |           });
  201 |         }
  202 |         await page.mouse.move(1, 1);
  203 |         await keyboardFocusSubmit(page, button);
  204 |         samples.push(await sampleButton(button, "keyboard-focus"));
  205 | 
  206 |         await button.hover();
  207 |         await page.mouse.down();
  208 |         try {
  209 |           samples.push(await sampleButton(button, "hover-active"));
  210 |           await page.mouse.move(1, 1);
  211 |           samples.push(await sampleButton(button, "active-without-hover"));
  212 |         } finally {
  213 |           await page.mouse.up();
  214 |           await page.mouse.move(1, 1);
  215 |         }
  216 | 
  217 |         const fieldStates = [];
  218 |         for (const field of ["username", "password"] as const) {
  219 |           const input = page.getByLabel(tr(language, `login.${field}`), {
  220 |             exact: true,
  221 |           });
  222 |           const wrapper = input.locator("xpath=..");
  223 |           await input.hover();
  224 |           await settleColors(wrapper);
  225 |           const hoverBorder = await wrapper.evaluate(
  226 |             (element) => getComputedStyle(element).borderColor,
  227 |           );
  228 |           await page.mouse.move(1, 1);
  229 |           await input.focus();
  230 |           await settleColors(wrapper);
  231 |           await expect(input).toBeFocused();
  232 |           const focus = await wrapper.evaluate((element) => {
  233 |             const style = getComputedStyle(element);
  234 |             return { border: style.borderColor, shadow: style.boxShadow };
  235 |           });
  236 |           expect(focus.shadow).not.toBe("none");
  237 |           fieldStates.push({ field, hoverBorder, focus });
  238 |         }
  239 |         const expectedTheme = preference === "light" ? "light" : "dark";
  240 |         await expect(page.locator("html")).toHaveAttribute(
  241 |           "data-theme",
  242 |           expectedTheme,
  243 |         );
  244 |         expect(
  245 |           await page.evaluate(() => localStorage.getItem("tds-theme")),
  246 |         ).toBe(expectedTheme);
  247 |         expect(submitted).toEqual([]);
  248 |         const report = {
  249 |           language,
  250 |           preference,
  251 |           bootstrap,
  252 |           samples,
  253 |           fieldStates,
  254 |           submitted,
  255 |         };
  256 |         await testInfo.attach("login-button-contrast", {
  257 |           body: JSON.stringify(report, null, 2),
  258 |           contentType: "application/json",
  259 |         });
  260 |         if (evidenceDir)
  261 |           await writeFile(
  262 |             path.join(evidenceDir, `${evidenceName}.json`),
  263 |             JSON.stringify(report, null, 2) + "\n",
  264 |           );
  265 | 
  266 |         expect(samples[0].hover).toBe(false);
  267 |         expect(samples[1].hover).toBe(true);
  268 |         expect(samples[2].focusVisible).toBe(true);
  269 |         expect(samples[2].hover).toBe(false);
  270 |         expect(samples[3].active).toBe(true);
  271 |         expect(samples[3].hover).toBe(true);
  272 |         expect(samples[4].active).toBe(true);
  273 |         expect(samples[4].hover).toBe(false);
  274 |         for (const sample of samples)
  275 |           expect(
  276 |             sample.ratio,
  277 |             `${sample.state}: ${sample.foreground} on ${sample.background}`,
> 278 |           ).toBeGreaterThanOrEqual(4.5);
      |             ^ Error: hover: rgb(255, 255, 255) on rgb(35, 146, 168)
  279 |       });
  280 |     }
  281 |   }
  282 | }
  283 | 
  284 | // The existing login/readiness suites retain disabled, bootstrap-pending,
  285 | // stable loading names, failure/retry and duplicate-submit behavior coverage.
  286 | 
```