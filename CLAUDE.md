# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- Build all modules: `./gradlew build`
- Run all tests: `./gradlew test`
- Run single module tests: `./gradlew :tart-core:test`
- Run specific test target: `./gradlew iosX64Test`
- Debug tests with: `./gradlew test --info`
- Lint: `./gradlew lint`

## Code Style Guidelines

### Architecture Pattern

- Follow the Tart state management pattern - one-way data flow
- State → Action → New State with optional Event emission

### Types and Interfaces

- Use sealed interfaces for State, Action, and Event hierarchies
- Implement proper marker interfaces (State, Action, Event)
- Use data classes/objects for concrete state implementations

### DSL Pattern

- Use the @TartStoreDsl annotation for builder APIs
- Follow the state{} and action{} block pattern
- Handle errors in dedicated error{} blocks

### Error Handling

- Use store.error{} for business logic errors
- Use store.exceptionHandler() for system errors
- Prefer modeling errors as state transitions

### Documentation

- Include state transition diagrams in tests
- Document experimental APIs with @ExperimentalTartApi
- Use KDoc comments for public APIs
