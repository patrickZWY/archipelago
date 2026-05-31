export const CONNECTION_CATEGORY_OPTIONS = [
  { value: "director", label: "Shared director" },
  { value: "cast", label: "Shared cast or crew" },
  { value: "genre", label: "Genre or subgenre" },
  { value: "theme", label: "Theme or idea" },
  { value: "tone", label: "Tone or mood" },
  { value: "structure", label: "Structure or form" },
  { value: "visual-style", label: "Visual style" },
  { value: "franchise", label: "Series or franchise" },
  { value: "era", label: "Era or movement" },
  { value: "influence", label: "Direct influence" },
] as const;

export type ConnectionCategory = typeof CONNECTION_CATEGORY_OPTIONS[number]["value"];

export function formatConnectionCategory(category: string | null | undefined) {
  if (!category) {
    return "Uncategorized";
  }

  return CONNECTION_CATEGORY_OPTIONS.find((option) => option.value === category)?.label ?? category;
}
