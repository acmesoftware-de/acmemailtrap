// ACMEsoftware Bar-Mark: four ASCENDING bars in the fixed brand colour sequence
// (Red - Yellow - Blue - Green). Geometry and colours per brand.acmesoftware.de
// (logo-mark.svg). Fixed colours — never themed.
export default function LogoGlyph({ height = 19 }: { height?: number }) {
  return (
    <svg
      viewBox="0 0 101 96"
      height={height}
      width={(height * 101) / 96}
      role="img"
      aria-label="ACMEmailtrap"
      xmlns="http://www.w3.org/2000/svg"
      style={{ display: 'block', flex: 'none' }}
    >
      <rect x="0" y="54" width="20" height="42" fill="#E63329" />
      <rect x="27" y="34" width="20" height="62" fill="#F6BE00" />
      <rect x="54" y="16" width="20" height="80" fill="#0057B8" />
      <rect x="81" y="0" width="20" height="96" fill="#1F9E5A" />
    </svg>
  )
}
