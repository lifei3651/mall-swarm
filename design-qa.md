# 售后申请页视觉验收

- Source visual truth:
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-f95c8823-3942-4285-a1d1-d618a9f9c1f1.png`
  - `/var/folders/nk/gpz82vss0513h6pd0tkfgs6h0000gn/T/codex-clipboard-93ed520b-8ece-428c-b67c-eeecab40ba08.png`
- Implementation screenshot: `document/qa/20260808-after-sale-application-mobile.png`
- Viewport: mobile CSS viewport requested at 390 × 844; screenshot is a full-page browser capture.
- Pixel dimensions:
  - source product region: 638 × 324
  - source redundant-tip region: 608 × 146
  - implementation full page: 1265 × 1238
- Density normalization: sources are cropped feedback images rather than a full-page mock. Comparison therefore used the readable product and notice regions as focused evidence, and judged the implementation at the browser surface's native capture density.
- State: logged-in customer, eligible completed order, two refundable products, all available quantities selected, refund application form open.

## Full-view comparison evidence

The rebuilt page keeps the existing storefront typography, white cards, red semantic accent and product imagery. The application state now contains one continuous task: product quantities, after-sale type, reason, estimated refund and submit. The ordinary order amount panel is hidden while applying, and the order number sits after the form at the bottom.

## Focused region comparison evidence

Focused comparison was required because the supplied sources are crops. Against the source product card, the implementation moves each amount to the right, uses bold red emphasis and keeps the quantity control in the same product row. Against the source notice crop, the redundant yellow notice is removed completely. No source image asset was recreated or replaced.

## Required fidelity surfaces

- Fonts and typography: existing storefront font stack and hierarchy retained; product amounts use a stronger red weight without changing the surrounding type scale.
- Spacing and layout rhythm: duplicate product-selection block and application-only amount panel removed; two after-sale type choices remain side by side on mobile to reduce vertical length.
- Colors and visual tokens: existing brand red, neutral borders and white card surfaces retained; no new color system introduced.
- Image quality and asset fidelity: real order product images remain in place with the existing crop and radius treatment.
- Copy and content: “已默认全选” confirms the initial state; the flow now reads “售后类型 → 申请原因 → 预计退款 → 提交申请”; duplicate amount guidance and estimate footnote are absent.

## Comparison history

1. First pass findings:
   - P2: the long default-selection message wrapped awkwardly.
   - P2: after-sale types stacked vertically and made the mobile form unnecessarily tall.
   - P2: the ordinary order amount panel still appeared below the application form.
2. Fixes:
   - shortened the message to “已默认全选”;
   - retained the two-column type selector on mobile and shortened its descriptions;
   - hid the order amount panel while the application form is active.
3. Post-fix evidence:
   - `document/qa/20260808-after-sale-application-mobile.png`
   - type switching and reason-sheet selection were exercised successfully in the browser; no visible runtime error appeared.

## Findings

No actionable P0, P1 or P2 mismatch remains for the requested flow and supplied focused references.

## Follow-up polish

- P3: after real-device release, confirm long customer product names still leave enough width for the amount and quantity selector.

final result: passed
