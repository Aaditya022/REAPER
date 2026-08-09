package com.stackd.ignition.analyzer;

/**
 * Describes the stack of a STACKD-generated project.
 *
 * <p>Mirrors STACKD's own enum values: React/Next/Vue/Angular/Django frontends,
 * Express/Express-TS/DRF backends, postgresql/mysql/mongodb databases,
 * Prisma/Drizzle ORMs, and JWT/NextAuth/Passport auth. Produced by the
 * ArchitectureAnalyzer and attached to a deployment when available.
 *
 * @param frontend the detected frontend framework
 * @param backend  the detected backend framework
 * @param database the detected database type
 * @param orm      the detected ORM
 * @param auth     the detected authentication mechanism
 */
public record DetectedStack(Frontend frontend, Backend backend, Database database, Orm orm, Auth auth) {

    /**
     * Frontend frameworks supported by STACKD.
     */
    public enum Frontend {
        /** No frontend was generated. */
        NONE,
        /** React with JavaScript (Vite). */
        REACT_JS,
        /** React with TypeScript (Vite). */
        REACT_TS,
        /** Next.js. */
        NEXT,
        /** Vue. */
        VUE,
        /** Angular. */
        ANGULAR
    }

    /**
     * Backend frameworks supported by STACKD.
     */
    public enum Backend {
        /** No backend was generated. */
        NONE,
        /** Express with JavaScript. */
        EXPRESS_JS,
        /** Express with TypeScript. */
        EXPRESS_TS,
        /** Django REST Framework. */
        DRF
    }

    /**
     * Database types supported by STACKD.
     */
    public enum Database {
        /** No database is used. */
        NONE,
        /** PostgreSQL. */
        POSTGRESQL,
        /** MySQL. */
        MYSQL,
        /** MongoDB. */
        MONGODB
    }

    /**
     * ORMs supported by STACKD.
     */
    public enum Orm {
        /** No ORM is used. */
        NONE,
        /** Prisma. */
        PRISMA,
        /** Drizzle. */
        DRIZZLE
    }

    /**
     * Authentication mechanisms supported by STACKD.
     */
    public enum Auth {
        /** No authentication was added. */
        NONE,
        /** JWT-based auth. */
        JWT,
        /** NextAuth-based auth. */
        NEXTAUTH,
        /** Passport-based auth. */
        PASSPORT
    }
}
