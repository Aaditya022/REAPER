"use client";

import { useState, useEffect, useRef } from "react";
import { ArrowRight, Check, Zap } from "lucide-react";

const plans = [
  {
    name: "Scaffold",
    description: "Interactive frontend and backend selection",
    price: { monthly: 0, annual: 0 },
    features: [
      "Frontend + backend stack prompts",
      "Architecture detection",
      "Environment validation",
      "Generate the project",
    ],
    cta: "reaper create",
    highlight: false,
  },
  {
    name: "Deploy",
    description: "Config generated, then shipped to Zerops",
    price: { monthly: 0, annual: 0 },
    features: [
      "zerops.yaml generated automatically",
      "One-command deployment",
      "Status tracked state by state",
      "Live URL returned",
    ],
    cta: "Deploy now",
    highlight: true,
  },
  {
    name: "Verify",
    description: "Ship only what passes health checks",
    price: { monthly: 0, annual: 0 },
    features: [
      "HTTPS health check",
      "3 consecutive unreachable attempts",
      "Clear failure states",
      "Healthy live URL",
    ],
    cta: "See the states",
    highlight: false,
  },
];

export function PricingSection() {
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
    <section id="pricing" ref={sectionRef} className="relative pt-20 lg:pt-28 pb-32 lg:pb-40">
      <div className="max-w-[1400px] mx-auto px-6 lg:px-12">
        {/* Header - Dramatic offset */}
        <div className="grid lg:grid-cols-12 gap-8 mb-16">
          <div className="lg:col-span-7">
            <span className="inline-flex items-center gap-3 text-sm font-medium uppercase tracking-wider text-muted-foreground mb-8">
              <span className="w-12 h-px bg-foreground/30" />
              Get started
            </span>
            <h2 className={`text-[2rem] md:text-[2.5rem] lg:text-[3.25rem] font-display font-bold tracking-[-0.01em] leading-[1.15] transition-all duration-1000 ${
              isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
            }`}>
              From
              <br />
              <span className="text-stroke">zero to live.</span>
            </h2>
          </div>
          
          <div className="lg:col-span-5 relative p-0 h-96 lg:h-auto">
            {/* Whale image */}
            <div className={`absolute inset-0 pointer-events-none transition-all duration-1000 delay-100 ${
              isVisible ? "opacity-100" : "opacity-0"
            }`}>
              <img
                src="/images/whale.png"
                alt=""
                aria-hidden="true"
                className="w-full h-full object-contain object-center"
              />
            </div>

          </div>
        </div>

        {/* Pricing cards - Horizontal layout with overlap */}
        <div className="relative">
          <div className="grid lg:grid-cols-3 gap-4 lg:gap-0">
            {plans.map((plan, index) => (
              <div
                key={plan.name}
                className={`relative bg-background border transition-all duration-700 ${
                  plan.highlight 
                    ? "border-foreground lg:-mx-2 lg:z-10 lg:scale-105" 
                    : "border-foreground/10 lg:first:-mr-2 lg:last:-ml-2"
                } ${isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-12"}`}
                style={{ transitionDelay: `${index * 100}ms` }}
              >
                {/* Popular badge */}
                {plan.highlight && (
                  <div className="absolute -top-4 left-8 right-8 flex justify-center">
                    <span className="inline-flex items-center gap-2 px-4 py-2 bg-foreground text-background text-sm font-medium uppercase tracking-wider">
                      <Zap className="w-3 h-3" />
                      Most Popular
                    </span>
                  </div>
                )}

                <div className="p-8 lg:p-10">
                  {/* Plan header */}
                  <div className="mb-8 pb-8 border-b border-foreground/10">
                    <span className="text-sm font-medium text-muted-foreground">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <h3 className="text-[1.5rem] lg:text-[1.875rem] font-display font-semibold leading-[1.25] mt-2">{plan.name}</h3>
                    <p className="text-lg text-muted-foreground mt-2">{plan.description}</p>
                  </div>

                  {/* Step number */}
                  <div className="mb-8">
                    <div className="flex items-baseline gap-2">
                      <span className="text-4xl lg:text-5xl font-display font-bold">
                        {String(index + 1).padStart(2, "0")}
                      </span>
                      <span className="text-base text-muted-foreground font-medium">step</span>
                    </div>
                    <p className="text-base text-muted-foreground mt-2">
                      part of the deploy flow
                    </p>
                  </div>

                  {/* Features */}
                  <ul className="space-y-3 mb-10">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-3">
                        <Check className="w-4 h-4 text-[#eca8d6] mt-0.5 shrink-0" />
                        <span className="text-base text-muted-foreground">{feature}</span>
                      </li>
                    ))}
                  </ul>

                  {/* CTA */}
                  <a
                    href="#how-it-works"
                    className={`w-full py-4 flex items-center justify-center gap-2 text-sm font-medium transition-all group ${
                      plan.highlight
                        ? "bg-foreground text-background hover:bg-foreground/90"
                        : "border border-foreground/20 text-foreground hover:border-foreground hover:bg-foreground/5"
                    }`}
                  >
                    {plan.cta}
                    <ArrowRight className="w-4 h-4 transition-transform group-hover:translate-x-1" />
                  </a>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom note with icons */}
        <div className={`mt-20 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-8 pt-12 border-t border-foreground/10 transition-all duration-1000 delay-500 ${
          isVisible ? "opacity-100" : "opacity-0"
        }`}>
          <div className="flex flex-wrap gap-6 text-base text-muted-foreground">
            <span className="flex items-center gap-2">
              <Check className="w-4 h-4 text-[#eca8d6]" />
              No hand-written YAML
            </span>
            <span className="flex items-center gap-2">
              <Check className="w-4 h-4 text-[#eca8d6]" />
              Verified health checks
            </span>
            <span className="flex items-center gap-2">
              <Check className="w-4 h-4 text-[#eca8d6]" />
              Secret-safe errors
            </span>
          </div>
          <a href="#features" className="text-sm underline underline-offset-4 hover:text-foreground transition-colors">
            See the capabilities
          </a>
        </div>
      </div>

      <style jsx>{`
        .text-stroke {
          -webkit-text-stroke: 1.5px currentColor;
          -webkit-text-fill-color: transparent;
        }
      `}</style>
    </section>
  );
}
