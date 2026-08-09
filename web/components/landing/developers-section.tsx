"use client";

import { useState, useEffect, useRef } from "react";

const features = [
  { 
    title: "Interactive scaffolding", 
    description: "Guided prompts select your frontend, backend, and database."
  },
  { 
    title: "Architecture detection", 
    description: "The generated project is inspected automatically before deploy."
  },
  { 
    title: "Config generation", 
    description: "The zerops.yaml is generated to match what was actually built."
  },
  { 
    title: "Status tracking", 
    description: "Watch the deployment move from PENDING to HEALTHY live."
  },
];

export function DevelopersSection() {
  const [isVisible, setIsVisible] = useState(false);
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
    <section id="developers" ref={sectionRef} className="relative pt-14 lg:pt-20 pb-24 lg:pb-28 overflow-hidden">

      {/* Image — absolute, bottom-right, behind all content */}
      <div
        className={`absolute bottom-0 right-0 w-[55%] h-[85%] pointer-events-none transition-all duration-1000 delay-300 ${
          isVisible ? "opacity-100" : "opacity-0"
        }`}
      >
        <img
          src="https://hebbkx1anhila5yf.public.blob.vercel-storage.com/Upscaled%20Image%20%2813%29-OQ2DiR3ElVsUg8kTvTL1kC5A3Q6maM.png"
          alt=""
          aria-hidden="true"
          className="w-full h-full object-cover object-left-top"
        />
        {/* Fade left edge */}
        <div className="absolute inset-0 bg-gradient-to-r from-background via-background/60 to-transparent" />
        {/* Fade top edge */}
        <div className="absolute inset-0 bg-gradient-to-b from-background via-transparent to-transparent" />
      </div>

      {/* All text content sits on top */}
      <div className="relative z-10 max-w-[1400px] mx-auto px-6 lg:px-12">
        {/* Header — Full width */}
        <div
          className={`mb-12 transition-all duration-700 ${
            isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
          }`}
        >
          <span className="inline-flex items-center gap-3 text-sm font-medium uppercase tracking-wider text-muted-foreground mb-6">
            <span className="w-8 h-px bg-foreground/30" />
            CLI-first
          </span>
          <h2 className="text-[2rem] md:text-[2.5rem] lg:text-[3.25rem] font-display font-bold tracking-[-0.01em] leading-[1.15]">
            Build it.
            <br />
            <span className="text-muted-foreground">Ship it.</span>
          </h2>
        </div>

        {/* Description + Features — left half only */}
        <div
          className={`max-w-[50%] transition-all duration-700 delay-100 ${
            isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
          }`}
        >
          <p className="text-xl text-muted-foreground mb-12 leading-[1.6] max-w-md">
            One command takes you from an interactive scaffold to a live Zerops deployment. Everything is generated, validated, and health-checked — no hand-written config.
          </p>
          <div className="grid grid-cols-2 gap-6">
            {features.map((feature, index) => (
              <div
                key={feature.title}
                className={`transition-all duration-500 ${
                  isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
                }`}
                style={{ transitionDelay: `${index * 50 + 200}ms` }}
              >
                <h3 className="text-[1.5rem] font-semibold leading-[1.25] mb-1">{feature.title}</h3>
                <p className="text-lg text-muted-foreground leading-[1.6]">{feature.description}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
