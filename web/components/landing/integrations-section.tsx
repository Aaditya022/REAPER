"use client";

import { useEffect, useState, useRef } from "react";

function Monogram({ letters }: { letters: string }) {
  return (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor">
      <text
        x="12"
        y="13"
        textAnchor="middle"
        dominantBaseline="central"
        fontSize="8.5"
        fontWeight="700"
        fontFamily="ui-sans-serif, system-ui, sans-serif"
        letterSpacing="-0.4"
      >
        {letters}
      </text>
    </svg>
  );
}

const logos: Record<string, React.ReactNode> = {
  React: (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="1.4">
      <ellipse cx="12" cy="12" rx="10" ry="4.4" />
      <ellipse cx="12" cy="12" rx="10" ry="4.4" transform="rotate(60 12 12)" />
      <ellipse cx="12" cy="12" rx="10" ry="4.4" transform="rotate(120 12 12)" />
      <circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none" />
    </svg>
  ),
  "Next.js": (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="none" stroke="currentColor" strokeWidth="1.4">
      <circle cx="12" cy="12" r="9.5" />
      <path d="M9 16V8h1.5l5.5 7V8h1.5v8h-1.5L10.5 9v7z" fill="currentColor" stroke="none" />
    </svg>
  ),
  Vue: (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor">
      <path d="M2.5 3h4L12 11 17.5 3h4L12 21 2.5 3zm8.2 0L12 5.2 13.3 3h-2.6z" />
    </svg>
  ),
  Angular: (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor">
      <path d="M12 2 2 5.6 4.6 19 12 22.8 19.4 19 22 5.6 12 2zm0 3.6 3.6 10h-2l-.7-2.2h-1.8l-.7 2.2h-2L12 5.6zm-.6 3 .7 1.9h-1.4l.7-1.9z" />
    </svg>
  ),
  ExpressJS: <Monogram letters="E" />,
  "Express + TypeScript": <Monogram letters="ET" />,
  "Django REST Framework": <Monogram letters="DJ" />,
  PostgreSQL: <Monogram letters="PG" />,
  MySQL: <Monogram letters="MY" />,
  MongoDB: (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor">
      <path d="M12 2c-1 6-4.5 8.4-4.5 13a4.5 4.5 0 0 0 9 0c0-4.6-3.5-7-4.5-13z" />
    </svg>
  ),
  Prisma: (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor">
      <path d="M6.5 5h11l-1.8 13.5L12 21l-6.3-2.5L6.5 5zm2.6 2 .9 11 5.3-7-6.2-4z" />
    </svg>
  ),
  Drizzle: (
    <svg viewBox="0 0 24 24" className="w-6 h-6" fill="currentColor">
      <path d="M12 2s6.5 7 6.5 12a6.5 6.5 0 0 1-13 0C5.5 9 12 2 12 2z" />
    </svg>
  ),
};

const integrations = [
  { name: "React", category: "Frontend" },
  { name: "Next.js", category: "Frontend" },
  { name: "Vue", category: "Frontend" },
  { name: "Angular", category: "Frontend" },
  { name: "ExpressJS", category: "Backend" },
  { name: "Express + TypeScript", category: "Backend" },
  { name: "Django REST Framework", category: "Backend" },
  { name: "PostgreSQL", category: "Database" },
  { name: "MySQL", category: "Database" },
  { name: "MongoDB", category: "Database" },
  { name: "Prisma", category: "ORM" },
  { name: "Drizzle", category: "ORM" },
];

export function IntegrationsSection() {
  const [isVisible, setIsVisible] = useState(false);
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [mousePos, setMousePos] = useState<{ x: number; y: number } | null>(null);
  const sectionRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) setIsVisible(true);
      },
      { threshold: 0.1 }
    );

    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <section id="integrations" ref={sectionRef} className="relative overflow-hidden">

      {/* Header — centred on the image below */}
      <div className="relative z-10 pt-20 lg:pt-28 text-center">
        <span className={`inline-flex items-center gap-4 text-sm font-medium uppercase tracking-wider text-muted-foreground mb-10 transition-all duration-700 justify-center ${
          isVisible ? "opacity-100" : "opacity-0"
        }`}>
          <span className="w-12 h-px bg-foreground/20" />
          Integrations
          <span className="w-12 h-px bg-foreground/20" />
        </span>

        <h2 className={`text-[2.5rem] md:text-[3.25rem] lg:text-[4.25rem] font-display font-bold tracking-[-0.01em] leading-[1.1] transition-all duration-1000 ${
          isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
        }`}>
          Build with
          <br />
          <span className="text-muted-foreground">your stack.</span>
        </h2>

        <p className={`mt-10 text-xl lg:text-2xl text-muted-foreground leading-[1.6] max-w-2xl mx-auto transition-all duration-1000 delay-100 ${
          isVisible ? "opacity-100" : "opacity-0"
        }`}>
          REAPER scaffolds the frontend, backend, database, and ORM you pick — then generates a deployment config that matches what was actually built.
        </p>

        {/* Category chips */}
        <div className={`mt-12 flex flex-wrap items-center justify-center gap-3 transition-all duration-1000 delay-200 ${
          isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
        }`}>
          {[
            { label: "Frontend", count: "4" },
            { label: "Backend", count: "3" },
            { label: "Database", count: "3" },
            { label: "ORM", count: "2" },
          ].map((chip) => (
            <span
              key={chip.label}
              className="inline-flex items-center gap-2 px-4 py-2 border border-foreground/15 bg-foreground/[0.02] text-base font-medium text-muted-foreground"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-[#eca8d6]" />
              {chip.label}
              <span className="text-muted-foreground/70">{chip.count}</span>
            </span>
          ))}
        </div>
      </div>

      {/* Full-width image */}
      <div className={`relative left-1/2 -translate-x-1/2 w-screen -mt-16 transition-all duration-1000 delay-200 ${
        isVisible ? "opacity-100" : "opacity-0"
      }`}>
        <img
          src="https://hebbkx1anhila5yf.public.blob.vercel-storage.com/connection-KeJwWPQvn6l0a7C48tCARYtNEdC92H.png"
          alt=""
          aria-hidden="true"
          className="w-full h-auto object-cover"
        />
      </div>

      {/* Integration grid — remonte sur l'image avec spacing mobile approprié */}
      <div className="relative z-10 mt-0 lg:-mt-24 max-w-[1400px] mx-auto px-6 lg:px-12">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 mb-16">
          {integrations.map((integration, index) => (
            <div
              key={integration.name}
              className={`group relative overflow-hidden p-6 lg:p-8 border transition-all duration-500 cursor-default ${
                hoveredIndex === index
                  ? "border-foreground bg-foreground/[0.04] scale-[1.02]"
                  : "border-foreground/10 hover:border-foreground/30"
              } ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"}`}
              style={{
                transitionDelay: `${index * 30 + 300}ms`,
              }}
              onMouseEnter={(e) => {
                setHoveredIndex(index);
                const rect = e.currentTarget.getBoundingClientRect();
                setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
              }}
              onMouseMove={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
              }}
              onMouseLeave={() => {
                setHoveredIndex(null);
                setMousePos(null);
              }}
            >
              {/* Cursor-following halo */}
              {hoveredIndex === index && mousePos && (
                <span
                  aria-hidden="true"
                  className="pointer-events-none absolute inset-0 z-0"
                  style={{
                    background: `radial-gradient(200px circle at ${mousePos.x}px ${mousePos.y}px, rgba(255,255,255,0.1) 0%, transparent 70%)`,
                  }}
                />
              )}
              {/* Category tag */}
              <span className={`absolute top-3 right-3 text-[11px] font-medium uppercase tracking-wider px-2 py-0.5 transition-colors ${
                hoveredIndex === index
                  ? "bg-foreground text-background"
                  : "bg-foreground/10 text-muted-foreground"
              }`}>
                {integration.category}
              </span>

              {/* Logo */}
              <div className={`w-10 h-10 mb-6 flex items-center justify-center transition-colors ${
                hoveredIndex === index ? "text-white" : "text-foreground/60"
              }`}>
                {logos[integration.name]}
              </div>

              <span className="text-lg font-medium block">{integration.name}</span>

              {/* Animated underline */}
              <div className="absolute bottom-0 left-0 right-0 h-px bg-foreground/20 overflow-hidden">
                <div className={`h-full bg-foreground transition-all duration-500 ${
                  hoveredIndex === index ? "w-full" : "w-0"
                }`} />
              </div>
            </div>
          ))}
        </div>

        {/* Bottom stats row */}
        <div className={`flex flex-wrap items-center justify-between gap-8 pt-12 border-t border-foreground/10 transition-all duration-1000 delay-500 pb-32 lg:pb-40 ${
          isVisible ? "opacity-100" : "opacity-0"
        }`}>
          <div className="flex flex-wrap gap-12">
            {[
              { value: "4", label: "Frontend frameworks" },
              { value: "3", label: "Backend frameworks" },
              { value: "3", label: "Databases" },
            ].map((stat) => (
              <div key={stat.label} className="flex items-baseline gap-3">
                <span className="text-3xl font-display font-bold">{stat.value}</span>
                <span className="text-base font-medium text-muted-foreground">{stat.label}</span>
              </div>
            ))}
          </div>

          <a href="#infra" className="group inline-flex items-center gap-2 text-base font-medium text-muted-foreground hover:text-foreground transition-colors">
            See all supported stacks
            <span className="group-hover:translate-x-1 transition-transform">&rarr;</span>
          </a>
        </div>
      </div>
    </section>
  );
}
