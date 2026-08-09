"use client";

import { useEffect, useState, useRef } from "react";

const stackCategories = [
  {
    category: "Frontend",
    stacks: ["React", "Next.js", "Vue", "Angular"],
  },
  {
    category: "Backend",
    stacks: ["ExpressJS", "Express + TypeScript", "Django REST Framework"],
  },
  {
    category: "Database",
    stacks: ["PostgreSQL", "MySQL", "MongoDB"],
  },
  {
    category: "ORM",
    stacks: ["Prisma", "Drizzle"],
  },
];

const stacks = stackCategories.flatMap((group) => group.stacks);

const quickFacts = [
  { value: "12", label: "stack options" },
  { value: "4", label: "categories covered" },
  { value: "1", label: "command to deploy" },
  { value: "0", label: "hand-written YAML" },
];

export function InfrastructureSection() {
  const [isVisible, setIsVisible] = useState(false);
  const [activeStack, setActiveStack] = useState(0);
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

  useEffect(() => {
    const interval = setInterval(() => {
      setActiveStack((prev) => (prev + 1) % stacks.length);
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  return (
    <section id="infra" ref={sectionRef} className="relative pt-20 lg:pt-28 pb-32 lg:pb-40 overflow-hidden">
      <div className="max-w-[1400px] mx-auto px-6 lg:px-12">
        {/* Header */}
        <div className="mb-16">
          <span className={`inline-flex items-center gap-4 text-sm font-medium uppercase tracking-wider text-muted-foreground mb-8 transition-all duration-700 ${
            isVisible ? "opacity-100" : "opacity-0"
          }`}>
            <span className="w-12 h-px bg-foreground/20" />
            Supported stacks
          </span>

          <div className="grid lg:grid-cols-[auto_1fr] gap-8 lg:gap-16 items-stretch">
            {/* Image globe — colonne gauche, pleine hauteur */}
            <div className={`w-48 lg:w-72 xl:w-80 shrink-0 transition-all duration-1000 ${
              isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
            }`}>
              <img
                src="https://hebbkx1anhila5yf.public.blob.vercel-storage.com/world-3i68QNWJwmO7W19ztZWbevAwJQHzYL.png"
                alt="Global network sphere"
                className="w-full h-full object-contain object-center"
              />
            </div>

            {/* Titre + description empilés */}
            <div className="flex flex-col justify-center">
              <h2 className={`text-[2rem] md:text-[2.5rem] lg:text-[3.25rem] font-display font-bold tracking-[-0.01em] leading-[1.15] transition-all duration-1000 ${
                isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
              }`}>
                Built on
                <br />
                your <span className="accent-word">stack.</span>
              </h2>

              <p className={`mt-8 text-xl text-muted-foreground leading-[1.6] max-w-lg transition-all duration-1000 delay-100 ${
                isVisible ? "opacity-100" : "opacity-0"
              }`}>
                REAPER scaffolds the technologies you actually use — frontend, backend, database, and ORM — and wires the deployment config to match.
              </p>
            </div>
          </div>

          {/* Quick facts row */}
          <div className={`mt-10 grid grid-cols-2 lg:grid-cols-4 gap-4 transition-all duration-1000 delay-200 ${
            isVisible ? "opacity-100" : "opacity-0"
          }`}>
            {quickFacts.map((fact) => (
              <div key={fact.label} className="flex items-baseline gap-3 border border-foreground/10 p-6">
                <span className="text-3xl lg:text-4xl font-display font-bold">{fact.value}</span>
                <span className="text-base font-medium text-muted-foreground">{fact.label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Main stats grid */}
        <div className="grid lg:grid-cols-3 gap-6">
          {/* Large stat card */}
          <div className={`lg:col-span-2 relative p-8 lg:p-12 border border-foreground/10 bg-foreground/[0.02] overflow-hidden transition-all duration-700 ${
            isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
          }`}>
            {/* Animated dots background with connecting lines */}
            <div className="absolute inset-0 opacity-70">
              <svg
                className="absolute inset-0 w-full h-full"
                style={{ pointerEvents: "none" }}
              >
                <defs>
                  <style>{`
                    @keyframes drawLine {
                      0%   { stroke-dashoffset: 1000; opacity: 0; }
                      15%  { opacity: 1; }
                      70%  { opacity: 0.7; }
                      100% { stroke-dashoffset: 0; opacity: 0; }
                    }
                    .connecting-line {
                      stroke: #eca8d6;
                      stroke-width: 1.2;
                      fill: none;
                      stroke-dasharray: 1000;
                      animation: drawLine 3s ease-in-out infinite;
                    }
                  `}</style>
                </defs>
                {[...Array(19)].map((_, i) => {
                  const x1 = 10 + (i % 5) * 20;
                  const y1 = 10 + Math.floor(i / 5) * 25;
                  const x2 = 10 + ((i + 1) % 5) * 20;
                  const y2 = 10 + Math.floor((i + 1) / 5) * 25;
                  return (
                    <line
                      key={`line-${i}`}
                      x1={`${x1}%`}
                      y1={`${y1}%`}
                      x2={`${x2}%`}
                      y2={`${y2}%`}
                      className="connecting-line"
                      style={{ animationDelay: `${i * 0.15}s` }}
                    />
                  );
                })}
              </svg>

              {/* Dots */}
              {[...Array(20)].map((_, i) => (
                <div
                  key={i}
                  className="absolute w-1.5 h-1.5 rounded-full bg-[#eca8d6]"
                  style={{
                    left: `${10 + (i % 5) * 20}%`,
                    top: `${10 + Math.floor(i / 5) * 25}%`,
                    animation: `pulse 2s ease-in-out ${i * 0.1}s infinite`,
                  }}
                />
              ))}
            </div>

            <div className="relative z-10">
              <div className="flex items-baseline gap-2 mb-4">
                <span className="text-5xl lg:text-6xl font-display font-bold leading-none">12</span>
                <span className="text-2xl font-medium text-muted-foreground">stack options</span>
              </div>
              <p className="text-lg text-muted-foreground max-w-md">
                Frontend, backend, database, and ORM choices that map to real scaffolding and a real Zerops deployment.
              </p>
            </div>
          </div>

          {/* Stacked stat cards */}
          <div className="flex flex-col gap-6">
            <div className={`p-8 border border-foreground/10 bg-foreground/[0.02] transition-all duration-700 delay-100 ${
              isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
            }`}>
              <span className="text-4xl lg:text-5xl font-display font-bold">155+</span>
              <span className="block text-base font-medium text-muted-foreground mt-2">Engine tests</span>
            </div>

            <div className={`p-8 border border-foreground/10 bg-foreground/[0.02] transition-all duration-700 delay-200 ${
              isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
            }`}>
              <span className="text-4xl lg:text-5xl font-display font-bold">2s</span>
              <span className="block text-base font-medium text-muted-foreground mt-2">Status polling</span>
            </div>
          </div>
        </div>

        {/* Stack matrix — every technology we scaffold */}
        <div className="mt-16">
          <div className="flex flex-col md:flex-row md:items-center gap-4 mb-8">
            <h3 className="text-xl lg:text-2xl font-display font-bold">Every stack we scaffold</h3>
            <div className="hidden md:block flex-1 h-px bg-foreground/10" />
            <span className="text-sm text-muted-foreground">12 technologies · 4 categories</span>
          </div>

          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {stackCategories.map((group) => (
              <div key={group.category} className="border border-foreground/10">
                <div className="px-6 py-4 border-b border-foreground/10 flex items-center justify-between">
                  <span className="text-sm font-medium uppercase tracking-wider text-muted-foreground">{group.category}</span>
                  <span className="text-sm text-muted-foreground">{group.stacks.length} stacks</span>
                </div>
                <ul className="p-3 flex flex-col gap-2">
                  {group.stacks.map((name) => {
                    const index = stacks.indexOf(name);
                    const active = activeStack === index;
                    return (
                      <li
                        key={name}
                        className={`flex items-center gap-3 px-4 py-3 border transition-all duration-300 cursor-default ${
                          active
                            ? "border-foreground/30 bg-foreground/[0.04]"
                            : "border-transparent"
                        }`}
                      >
                        <span className={`w-2 h-2 rounded-full transition-colors ${
                          active ? "bg-[#eca8d6]" : "bg-foreground/20"
                        }`} />
                        <span className="text-lg font-semibold">{name}</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom line */}
        <div className={`mt-16 pt-10 border-t border-foreground/10 flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6 transition-all duration-1000 delay-300 ${
          isVisible ? "opacity-100" : "opacity-0"
        }`}>
          <p className="text-lg text-muted-foreground max-w-2xl leading-[1.6]">
            Pick a frontend, backend, database, and ORM through guided prompts. REAPER detects the architecture, generates the zerops.yaml, and ships it — no hand-written config.
          </p>
          <span className="font-mono text-sm text-muted-foreground whitespace-nowrap">
            reaper create — picks your stack for you
          </span>
        </div>
      </div>
    </section>
  );
}
