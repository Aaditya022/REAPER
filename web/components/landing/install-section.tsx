"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Check, Copy, Github, Terminal } from "lucide-react";

const GITHUB_URL = "https://github.com/Aaditya022/REAPER";
const INSTALL_URL = "https://web-2cea-3000.prg1.zerops.app";

const INSTALL_METHODS = [
  {
    id: "curl",
    label: "curl",
    command: `curl -fsSL ${INSTALL_URL}/install.sh | bash`,
  },
  { id: "npm", label: "npm", command: "npm install -g reaper" },
  { id: "bun", label: "bun", command: "bun add -g reaper" },
  { id: "brew", label: "brew", command: "brew install reaper" },
  { id: "paru", label: "paru", command: "paru -S reaper" },
] as const;

type InstallMethodId = (typeof INSTALL_METHODS)[number]["id"];

export function InstallSection() {
  const [activeTab, setActiveTab] = useState<InstallMethodId>("curl");
  const [copied, setCopied] = useState(false);
  const [isVisible, setIsVisible] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const activeMethod =
    INSTALL_METHODS.find((method) => method.id === activeTab) ?? INSTALL_METHODS[0];

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
    return () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    };
  }, []);

  const handleCopy = async () => {
    const command = activeMethod.command;

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(command);
      } else {
        throw new Error("Clipboard API unavailable");
      }
    } catch {
      const textarea = document.createElement("textarea");
      textarea.value = command;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      textarea.style.pointerEvents = "none";
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      try {
        document.execCommand("copy");
      } finally {
        document.body.removeChild(textarea);
      }
    }

    setCopied(true);
    if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    copyTimerRef.current = setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section
      id="install"
      ref={sectionRef}
      className="relative pt-14 lg:pt-20 pb-24 lg:pb-28 overflow-hidden"
    >
      {/* Background glows */}
      <div
        aria-hidden="true"
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[900px] h-[560px] rounded-full bg-[#eca8d6]/[0.06] blur-[140px] pointer-events-none"
      />
      <div
        aria-hidden="true"
        className="absolute -top-24 right-[8%] w-[480px] h-[380px] rounded-full bg-[#2dd4bf]/[0.05] blur-[120px] pointer-events-none"
      />

      <div className="relative z-10 max-w-[1400px] mx-auto px-6 lg:px-12">
        {/* Header */}
        <div
          className={`mx-auto max-w-2xl text-center transition-all duration-1000 ${
            isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
          }`}
        >
          <span className="inline-flex items-center gap-4 text-sm font-medium uppercase tracking-wider text-muted-foreground mb-8">
            <span className="w-12 h-px bg-foreground/20" />
            Get started
            <span className="w-12 h-px bg-foreground/20" />
          </span>

          <h2 className="text-[2rem] md:text-[2.5rem] lg:text-[3.25rem] font-display font-bold tracking-[-0.01em] leading-[1.15]">
            Run REAPER <span className="accent-word">anywhere.</span>
          </h2>

          <p className="mt-6 text-xl text-muted-foreground leading-[1.6]">
            Get started with REAPER directly from your terminal. Choose your preferred
            package manager and run the command.
          </p>
        </div>

        {/* macOS Terminal window */}
        <div
          className={`mt-14 lg:mt-16 transition-all duration-1000 delay-200 ${
            isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-10"
          }`}
        >
          <div className="relative mx-auto max-w-3xl">
            {/* Ambient glow behind the window */}
            <div
              aria-hidden="true"
              className="absolute -inset-5 rounded-[2rem] bg-gradient-to-r from-[#eca8d6]/15 via-[#a78bfa]/10 to-[#67e8f9]/15 blur-2xl pointer-events-none"
            />

            <div className="relative rounded-2xl overflow-hidden border border-white/10 bg-[#0b0d10]/95 backdrop-blur-xl shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)]">
              {/* macOS title bar */}
              <div className="flex items-center gap-3 px-4 lg:px-5 h-12 border-b border-white/10 bg-gradient-to-b from-white/[0.07] to-transparent select-none">
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full bg-[#ff5f57]" />
                  <span className="w-3 h-3 rounded-full bg-[#febc2e]" />
                  <span className="w-3 h-3 rounded-full bg-[#28c840]" />
                </div>

                <div className="flex-1 min-w-0 flex items-center justify-center gap-2 font-mono text-xs text-white/50 truncate">
                  <Terminal className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                  <span className="truncate">REAPER — Terminal</span>
                </div>

                <div className="w-[60px]" aria-hidden="true" />
              </div>

              {/* Installation method tabs */}
              <div className="flex items-center gap-1.5 px-4 lg:px-5 pt-3 pb-2.5 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden border-b border-white/5 bg-white/[0.02]">
                {INSTALL_METHODS.map((method) => (
                  <button
                    key={method.id}
                    type="button"
                    onClick={() => {
                      setActiveTab(method.id);
                      setCopied(false);
                    }}
                    aria-pressed={activeTab === method.id}
                    className={`shrink-0 font-mono text-xs md:text-sm px-3 md:px-4 py-1.5 rounded-md border transition-colors duration-200 ${
                      activeTab === method.id
                        ? "bg-white/10 text-white border-white/15"
                        : "text-white/40 border-transparent hover:text-white/70 hover:bg-white/5"
                    }`}
                  >
                    {method.label}
                  </button>
                ))}
              </div>

              {/* Command line */}
              <div className="px-4 lg:px-5 py-6 lg:py-8">
                <div className="flex items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="shrink-0 font-mono text-sm md:text-base text-emerald-400/90 font-semibold"
                  >
                    $
                  </span>

                  <div className="flex-1 min-w-0 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                    <code className="font-mono text-sm md:text-base text-white/90 whitespace-nowrap">
                      {activeMethod.command}
                    </code>
                    <span className="terminal-cursor" aria-hidden="true" />
                  </div>

                  <button
                    type="button"
                    onClick={handleCopy}
                    aria-label={copied ? "Copied to clipboard" : "Copy command"}
                    className={`shrink-0 inline-flex items-center justify-center gap-1.5 rounded-md border font-mono text-xs font-medium h-9 w-[104px] transition-colors duration-200 ${
                      copied
                        ? "border-emerald-400/30 bg-emerald-400/10 text-emerald-300"
                        : "border-white/10 bg-white/5 text-white/70 hover:text-white hover:bg-white/10"
                    }`}
                  >
                    {copied ? (
                      <>
                        <Check className="w-3.5 h-3.5" aria-hidden="true" />
                        Copied!
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" aria-hidden="true" />
                        Copy
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Secondary line */}
        <p
          className={`mt-8 text-center text-base font-medium text-muted-foreground transition-all duration-1000 delay-300 ${
            isVisible ? "opacity-100" : "opacity-0"
          }`}
        >
          Open source · Developer friendly · Ready in seconds
        </p>

        {/* GitHub CTA */}
        <div
          className={`mt-8 flex justify-center transition-all duration-1000 delay-400 ${
            isVisible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
          }`}
        >
          <Button
            asChild
            variant="outline"
            size="lg"
            className="h-14 px-8 text-base font-medium tracking-[0.02em] rounded-full border-foreground/20 hover:bg-foreground/5"
          >
            <a href={GITHUB_URL} target="_blank" rel="noreferrer">
              <Github className="w-4 h-4" aria-hidden="true" />
              View on GitHub
            </a>
          </Button>
        </div>
      </div>

      <style jsx>{`
        .terminal-cursor {
          display: inline-block;
          width: 0.6em;
          height: 1.05em;
          margin-left: 2px;
          vertical-align: -0.2em;
          background: rgba(255, 255, 255, 0.85);
          animation: reaper-cursor-blink 1.1s steps(2, start) infinite;
        }
        @keyframes reaper-cursor-blink {
          to {
            visibility: hidden;
          }
        }
      `}</style>
    </section>
  );
}
