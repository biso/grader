![example workflow](https://github.com/biso/grader/actions/workflows/scala.yml/badge.svg)
![example workflow2](https://github.com/biso/grader/actions/workflows/codacy.yml/badge.svg)

## To run tests

- You need a recent JVM (>= JDK8)
- ./sbt test

## To open in VSCode

- Open the project directory, VSCode will take care of the rest
    * install VSCode scala extension
    * install bsp and bloop 

## Subprojects

### common

- Common code

### hue

- Convenient wrappers for the Phillips Hue REST API.

### prepare

- Collects submissions and tests and prepares for a final grading run

### reporter

Creates project repos
Generates HTML reports
Sends e-mail reports

### run

- Makes one run over the repos prepared by `prepare`

### runner

- Detects new tests and submissions
- Runs tests as needed
- Generate results in appropriate repos

### status

- collects current results and uses the `hue` API to change light colors in my office

### sync_repos

- Keeps local repos in sync with remote ones. Mostly for backup and browsing

### 
