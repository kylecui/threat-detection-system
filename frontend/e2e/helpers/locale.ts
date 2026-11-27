import { readFileSync } from "node:fs";
import path from "node:path";

export type E2ELanguage = "zh-CN" | "en-US";

const cache = new Map<E2ELanguage, Record<string, unknown>>();

function messages(language: E2ELanguage): Record<string, unknown> {
  if (!cache.has(language)) {
    const file = path.resolve(__dirname, "../../src/i18n/locales", `${language}.json`);
    cache.set(language, JSON.parse(readFileSync(file, "utf-8")));
  }
  return cache.get(language)!;
}

/** Translate a production i18n key so locators work in both languages. */
export function tr(language: E2ELanguage, key: string): string {
  const value = key
    .split(".")
    .reduce<unknown>((node, part) => (node && typeof node === "object" ? (node as Record<string, unknown>)[part] : undefined), messages(language));
  if (typeof value !== "string") throw new Error(`Missing ${language} translation for ${key}`);
  return value;
}
