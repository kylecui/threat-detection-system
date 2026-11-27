/**
 * INTERACTION-004: in-page table layout checks shared by every surface.
 *
 * `inspectTables` runs inside the browser (page.evaluate) and must stay
 * self-contained. It checks every visible table under the given scope:
 * computed styles, real geometry (centring, column alignment), action groups,
 * internal horizontal scrolling with a still-clickable fixed column, page
 * overflow and (optionally) text contrast.
 */

export interface InspectOptions {
  /** CSS selector of the scope root (main content, open modal or drawer). */
  scope: string;
  /** Allowed centre offset in CSS px. */
  tolerance: number;
  /** Check text/background contrast of body cells. */
  checkContrast: boolean;
}

export interface InspectReport {
  tables: number;
  cells: number;
  rows: number;
  problems: string[];
  scrolledContainers: number;
  pageOverflow: number;
  /** Visible loading spinners inside the scope (tables still loading). */
  spinning: number;
  /** A modal/drawer enter animation is still running. */
  animating: boolean;
}

export function inspectTables(options: InspectOptions): InspectReport {
  const { scope, tolerance, checkContrast } = options;
  const problems: string[] = [];
  const roots = Array.from(document.querySelectorAll<HTMLElement>(scope));
  const visible = (el: Element) => {
    const box = (el as HTMLElement).getBoundingClientRect();
    return !!(el as HTMLElement).offsetParent && box.width > 0 && box.height > 0;
  };
  const label = (cell: HTMLTableCellElement) =>
    `${cell.tagName}#${cell.cellIndex} "${(cell.textContent || "").trim().slice(0, 24)}"`;
  const rect = (el: Element) => el.getBoundingClientRect();

  const tables = roots
    .flatMap((root) => Array.from(root.querySelectorAll<HTMLElement>(".ant-table")))
    .filter(visible);

  let cells = 0;
  let rows = 0;
  let scrolledContainers = 0;

  // ------------------------------------------------------ contrast helpers
  const parseColor = (value: string): [number, number, number, number] | null => {
    const m = value.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const parts = m[1].split(/[\s,/]+/).filter(Boolean).map(Number);
    return [parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 1];
  };
  const effectiveBackground = (el: Element): [number, number, number] => {
    let node: Element | null = el;
    while (node) {
      const color = parseColor(getComputedStyle(node).backgroundColor);
      if (color && color[3] > 0.5) return [color[0], color[1], color[2]];
      node = node.parentElement;
    }
    return [255, 255, 255];
  };
  const luminance = ([r, g, b]: number[]) => {
    const channel = (v: number) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
  };
  const contrast = (a: number[], b: number[]) => {
    const [l1, l2] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (l1 + 0.05) / (l2 + 0.05);
  };

  for (const table of tables) {
    const tableName = table.closest("[data-surface-table]")?.getAttribute("data-surface-table") || `table#${tables.indexOf(table)}`;
    // Only this table's own rows (not rows of nested tables).
    const own = <T extends Element>(selector: string) =>
      Array.from(table.querySelectorAll<T>(selector)).filter((el) => el.closest(".ant-table") === table);

    const headerCells = own<HTMLTableCellElement>(".ant-table-thead > tr > th").filter(
      (cell) => visible(cell) && !cell.classList.contains("ant-table-cell-scrollbar"),
    );
    const bodyRows = own<HTMLTableRowElement>(".ant-table-tbody > tr.ant-table-row").filter(visible);
    rows += bodyRows.length;
    const bodyCells = bodyRows.flatMap((row) => Array.from(row.cells)).filter(visible);

    // 1. computed style contract
    for (const cell of [...headerCells, ...bodyCells]) {
      cells += 1;
      const style = getComputedStyle(cell);
      if (style.textAlign !== "center") problems.push(`${tableName} ${label(cell)} text-align=${style.textAlign}`);
      if (style.verticalAlign !== "middle") problems.push(`${tableName} ${label(cell)} vertical-align=${style.verticalAlign}`);
      if (style.whiteSpace !== "nowrap") problems.push(`${tableName} ${label(cell)} white-space=${style.whiteSpace}`);
    }

    // 2. geometric centring of compound content
    const centred = [
      ".tds-table-actions",
      ".tds-table-tags",
      ".tds-status-tag",
      ".ant-switch",
      ".tds-network-identifier",
      ".ant-checkbox-wrapper",
      ".ant-table-column-sorters",
      ".ant-table-filter-column",
    ];
    /** Sorter/filter containers stretch across the cell: measure title + icon. */
    const groupBox = (el: HTMLElement): DOMRect => {
      const parts = Array.from(
        el.querySelectorAll<HTMLElement>(":scope > .ant-table-column-title, :scope > .ant-table-column-sorter, :scope > .ant-table-filter-trigger, :scope > span > .ant-table-column-sorter"),
      ).filter(visible);
      if (!parts.length) return rect(el);
      const boxes = parts.map(rect);
      const left = Math.min(...boxes.map((b) => b.left));
      const right = Math.max(...boxes.map((b) => b.right));
      const top = Math.min(...boxes.map((b) => b.top));
      const bottom = Math.max(...boxes.map((b) => b.bottom));
      return new DOMRect(left, top, right - left, bottom - top);
    };
    for (const cell of [...headerCells, ...bodyCells]) {
      const cellBox = rect(cell);
      const style = getComputedStyle(cell);
      const innerLeft = cellBox.left + parseFloat(style.paddingLeft);
      const innerRight = cellBox.right - parseFloat(style.paddingRight);
      for (const selector of centred) {
        const el = cell.querySelector<HTMLElement>(`:scope ${selector}`);
        if (!el || el.closest(".ant-table") !== table || !visible(el)) continue;
        const box = selector === ".ant-table-column-sorters" || selector === ".ant-table-filter-column" ? groupBox(el) : rect(el);
        // Content intentionally clipped by an ellipsis column is not "centred".
        if (box.width > innerRight - innerLeft + 1) continue;
        const dx = box.left + box.width / 2 - (innerLeft + innerRight) / 2;
        const dy = box.top + box.height / 2 - (cellBox.top + cellBox.height / 2);
        if (Math.abs(dx) > tolerance || Math.abs(dy) > tolerance) {
          problems.push(`${tableName} ${label(cell)} ${selector} off-centre dx=${dx.toFixed(1)} dy=${dy.toFixed(1)}`);
        }
        break;
      }
    }

    // 3. action groups: one line, inside the cell
    for (const group of own<HTMLElement>(".tds-table-actions").filter(visible)) {
      const buttons = Array.from(group.querySelectorAll("button")).filter(visible);
      const tops = new Set(buttons.map((button) => Math.round(rect(button).top)));
      if (tops.size > 1) problems.push(`${tableName} action buttons wrapped (${tops.size} lines)`);
      const cell = group.closest("td");
      if (cell) {
        const g = rect(group);
        const c = rect(cell);
        if (g.left < c.left - 0.5 || g.right > c.right + 0.5) problems.push(`${tableName} action group overflows its cell`);
      }
    }

    // 3b. every button in a table has an accessible name (icon buttons use aria-label)
    for (const button of bodyCells.flatMap((cell) => Array.from(cell.querySelectorAll("button"))).filter(visible)) {
      const name = (button.getAttribute("aria-label") || button.textContent || button.getAttribute("title") || "").trim();
      if (!name) problems.push(`${tableName} button without an accessible name`);
    }

    // 4. header / body column edges line up
    const firstRow = bodyRows[0];
    const headerRow = own<HTMLTableRowElement>(".ant-table-thead > tr")[0];
    const alignColumns = (when: string) => {
      if (!firstRow || !headerRow) return;
      const headers = Array.from(headerRow.cells).filter((c) => c.colSpan === 1 && visible(c) && !c.classList.contains("ant-table-cell-scrollbar"));
      const body = Array.from(firstRow.cells).filter((c) => c.colSpan === 1 && visible(c));
      if (headers.length !== body.length) return;
      headers.forEach((th, index) => {
        const a = rect(th);
        const b = rect(body[index]);
        if (Math.abs(a.left - b.left) > 1 || Math.abs(a.right - b.right) > 1) {
          problems.push(`${tableName} column ${index} header/body misaligned ${when} (${a.left.toFixed(0)}-${a.right.toFixed(0)} vs ${b.left.toFixed(0)}-${b.right.toFixed(0)})`);
        }
      });
    };
    alignColumns("before scroll");

    // 5. internal horizontal scroll really scrolls; fixed column stays usable
    const scrollers = own<HTMLElement>(".ant-table-content, .ant-table-body").filter(
      (el) => visible(el) && el.scrollWidth > el.clientWidth + 1,
    );
    for (const scroller of scrollers) {
      scrolledContainers += 1;
      const overscroll = getComputedStyle(scroller).overscrollBehaviorX;
      if (overscroll !== "contain") problems.push(`${tableName} overscroll-behavior-x=${overscroll}`);
      const before = scroller.scrollLeft;
      scroller.scrollLeft = scroller.scrollWidth;
      scroller.dispatchEvent(new Event("scroll"));
      if (scroller.scrollLeft <= before) problems.push(`${tableName} horizontal overflow but scrollLeft did not change`);
      const fixedCell = own<HTMLTableCellElement>(".ant-table-tbody td.ant-table-cell-fix-right").filter(visible)[0];
      if (fixedCell) {
        // Bring the row into the window first: elementFromPoint only sees the viewport.
        fixedCell.scrollIntoView({ block: "center", inline: "nearest" });
        scroller.scrollLeft = scroller.scrollWidth;
        const box = rect(scroller);
        const f = rect(fixedCell);
        if (f.right > box.right + 1 || f.left < box.left - 1) {
          problems.push(`${tableName} fixed column left the scroll viewport (cell ${f.left.toFixed(0)}-${f.right.toFixed(0)}, viewport ${box.left.toFixed(0)}-${box.right.toFixed(0)})`);
        }
        const button = fixedCell.querySelector("button");
        if (button && visible(button)) {
          const b = rect(button);
          const hit = document.elementFromPoint(b.left + b.width / 2, b.top + b.height / 2);
          if (!hit || !button.contains(hit)) {
            problems.push(`${tableName} fixed action button is covered after scrolling (hit: ${hit ? (hit as HTMLElement).className || hit.tagName : "none"})`);
          }
        }
      }
      alignColumns("after scroll");
      scroller.scrollLeft = before;
      scroller.dispatchEvent(new Event("scroll"));
    }

    // 6. contrast of plain body text (dark/light theme)
    if (checkContrast) {
      for (const cell of bodyCells.slice(0, 40)) {
        const text = (cell.textContent || "").trim();
        if (!text || cell.querySelector("button, .ant-tag, .tds-network-identifier, .ant-switch")) continue;
        const fg = parseColor(getComputedStyle(cell).color);
        if (!fg) continue;
        const ratio = contrast([fg[0], fg[1], fg[2]], effectiveBackground(cell));
        if (ratio < 4.5) problems.push(`${tableName} ${label(cell)} contrast ${ratio.toFixed(2)} < 4.5`);
      }
    }
  }

  // 7. nothing is clipped: the scope (and its body) is not wider than its box.
  // `.tds-content` uses overflow-x: hidden, so a too-wide child would be cut
  // off silently without any page-level horizontal scrollbar.
  for (const root of roots.filter(visible)) {
    const boxes = [root, ...Array.from(root.querySelectorAll<HTMLElement>(":scope .tds-content__inner, :scope .ant-modal-body, :scope .ant-drawer-body"))];
    for (const box of boxes.filter(visible)) {
      if (box.scrollWidth > box.clientWidth + 1) {
        problems.push(`${box.className.split(" ")[0] || box.tagName} content overflows its container (${box.scrollWidth} > ${box.clientWidth}) and is clipped or scrolls outside the table`);
      }
    }
  }

  const spinning = roots
    .flatMap((root) => Array.from(root.querySelectorAll(".ant-spin-spinning")))
    .filter(visible).length;
  // Only motion on the scope itself or its ancestors matters (modal zoom,
  // drawer slide); unrelated motion such as the sidebar submenu is ignored.
  const motion = "[class*='-appear-active'], [class*='-enter-active'], [class*='-appear-start'], [class*='-enter-start'], [class*='-appear-prepare'], [class*='-enter-prepare']";
  const animating = roots.some((root) => !!root.closest(motion));

  return {
    spinning,
    animating,
    tables: tables.length,
    cells,
    rows,
    problems,
    scrolledContainers,
    pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  };
}
