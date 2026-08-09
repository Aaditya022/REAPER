"use client";

import { useEffect, useState, useRef } from "react";
import { Shield, Lock, Eye, FileCheck } from "lucide-react";

const securityFeatures = [
  {
    icon: Shield,
    title: "Secret-safe errors",
    description: "Stack traces never leak env values or API tokens.",
    image: "/images/isolated.jpg",
  },
  {
    icon: Lock,
    title: "Environment validation",
    description: "Required variables are validated before deployment starts.",
    image: "/images/encrypted.jpg",
  },
  {
    icon: Eye,
    title: "Live health checks",
    description: "Every deployment is verified over HTTPS before it ships.",
    image: "/images/audit.jpg",
  },
  {
    icon: FileCheck,
    title: "Fail-fast status",
    description: "Unreachable deployments are tracked and failed cleanly.",
    image: "/images/permissions.jpg",
  },
];

const certifications = ["Secret-safe", "Env-validated", "HTTPS checks", "Fail-fast"];

export function SecuritySection() {
  const [isVisible, setIsVisible] = useState(false);
  const [activeFeature, setActiveFeature] = useState(0);
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
      setActiveFeature((prev) => (prev + 1) % securityFeatures.length);
    }, 3000);
    return () => clearInterval(interval);
  }, []);

  return (
    <section ref={sectionRef} className="py-24 lg:py-32">
      <div className="max-w-[1400px] mx-auto px-6 lg:px-12">
        {/* Header */}
        <div className="mb-16">
          <span
            className={`inline-flex items-center gap-4 text-sm font-medium uppercase tracking-wider text-muted-foreground mb-8 transition-all duration-700 ${
              isVisible ? "opacity-100" : "opacity-0"
            }`}
          >
            <span className="w-12 h-px bg-foreground/20" />
            Security
          </span>

          {/* Title — full width */}
          <h2
            className={`text-[2rem] md:text-[2.5rem] lg:text-[3.25rem] font-display font-bold tracking-[-0.01em] leading-[1.1] mb-8 transition-all duration-1000 ${
              isVisible
                ? "opacity-100 translate-y-0"
                : "opacity-0 translate-y-8"
            }`}
          >
            Deployed,
            <br />
            not <span className="accent-word">exposed.</span>
          </h2>

          {/* Description — below title */}
          <div
            className={`transition-all duration-1000 delay-100 ${
              isVisible ? "opacity-100" : "opacity-0"
            }`}
          >
            <p className="text-xl lg:text-2xl text-muted-foreground leading-[1.55] max-w-3xl">
              REAPER keeps your credentials local, validates your environment
              before deploy, and verifies the live URL over HTTPS before
              anything ships.
            </p>
          </div>
        </div>

        {/* Main content */}
        <div className="grid lg:grid-cols-12 gap-6">
          {/* Large visual card */}
          <div
            className={`lg:col-span-7 relative p-8 lg:p-12 border border-foreground/10 min-h-[400px] overflow-hidden transition-all duration-700 ${
              isVisible
                ? "opacity-100 translate-y-0"
                : "opacity-0 translate-y-8"
            }`}
          >
            {/* Dynamic feature image with cross-fade — desktop only */}
            {/* Image positioning, size, and animation intentionally unchanged. */}
            <div className="absolute inset-0 pointer-events-none hidden lg:block">
              {securityFeatures.map((feature, index) => (
                <img
                  key={feature.image}
                  src={feature.image}
                  alt={feature.title}
                  className="absolute inset-0 flex items-center justify-end h-3/4 w-3/4 ml-auto object-contain object-right transition-opacity duration-500"
                  style={{ opacity: activeFeature === index ? 0.85 : 0 }}
                />
              ))}
            </div>

            {/* Text is constrained to the left side so it never occupies the image area. */}
            <div className="relative z-10 w-full lg:max-w-[34%]">
              <span className="text-lg lg:text-xl font-medium text-muted-foreground">
                Active verification
              </span>

              <div className="mt-7">
                <span className="text-6xl lg:text-7xl leading-none font-display font-bold">
                  3
                </span>

                <span className="block text-lg lg:text-xl text-muted-foreground mt-5 leading-[1.45]">
                  Consecutive unreachable attempts before a deployment fails
                </span>
              </div>
            </div>

            {/* Certification badges */}
            <div className="absolute bottom-8 left-8 right-8 flex flex-wrap gap-2">
              {certifications.map((cert, index) => (
                <span
                  key={cert}
                  className={`px-3 py-1 border border-foreground/10 text-sm font-medium uppercase tracking-wider text-muted-foreground transition-all duration-500 ${
                    isVisible
                      ? "opacity-100 translate-y-0"
                      : "opacity-0 translate-y-4"
                  }`}
                  style={{ transitionDelay: `${index * 100 + 300}ms` }}
                >
                  {cert}
                </span>
              ))}
            </div>
          </div>

          {/* Feature cards stack */}
          <div className="lg:col-span-5 flex flex-col gap-4">
            {securityFeatures.map((feature, index) => (
              <div
                key={feature.title}
                className={`p-6 border transition-all duration-500 cursor-default ${
                  activeFeature === index
                    ? "border-foreground/30 bg-foreground/[0.04]"
                    : "border-foreground/10"
                } ${
                  isVisible
                    ? "opacity-100 translate-x-0"
                    : "opacity-0 translate-x-8"
                }`}
                style={{ transitionDelay: `${index * 80}ms` }}
                onClick={() => setActiveFeature(index)}
                onMouseEnter={() => setActiveFeature(index)}
              >
                <div className="flex items-start gap-4">
                  <div
                    className={`shrink-0 w-10 h-10 flex items-center justify-center border transition-colors ${
                      activeFeature === index
                        ? "border-foreground bg-foreground text-background"
                        : "border-foreground/20"
                    }`}
                  >
                    <feature.icon className="w-5 h-5" />
                  </div>

                  <div>
                    <h3 className="text-[1.5rem] font-semibold leading-[1.25] mb-1">
                      {feature.title}
                    </h3>
                    <p className="text-lg text-muted-foreground leading-[1.6]">
                      {feature.description}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}