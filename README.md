# MotionApp

MotionApp is an Android-based health monitoring application designed to help detect, record, and review abnormal motion events, such as possible epileptic seizures, motor tics, tremors, falls, or other unusual movement patterns. The application uses accelerometer and gyroscope data from a mobile device and connected smartwatch to monitor motion activity in real time, save recorded sessions, and make the data available for later review or analysis.

This project was developed as part of the Heartland Computing Health Application capstone project at Central Washington University.

## Important Medical Disclaimer

MotionApp is not intended to provide a medical diagnosis, replace medical care, or confirm that a seizure, tic, tremor, or fall has occurred. The application is designed as a monitoring and data collection tool that records motion sensor data for review, testing, and future analysis.

## Purpose

The purpose of MotionApp is to support health-related motion monitoring by collecting sensor data that may help identify abnormal movement events, including possible epileptic seizures, tics, tremors, falls, or other irregular motion patterns.

The app allows users to start and stop monitoring sessions, view live accelerometer and gyroscope data, save recorded motion data as CSV files, and review or share past sessions. This data may be useful for future analysis, caregiver review, testing, or machine learning-based motion prediction.

## Project Overview

MotionApp provides a mobile and wearable motion-monitoring system. During an active monitoring session, the app collects sensor readings from the phone and/or smartwatch, displays live motion data, and stores the completed session for later review.

The system includes a mobile Android application, a smartwatch application, Firebase authentication, Firebase data storage, local CSV file generation, and session history features.

## Main Features

- Start and stop health-monitoring sessions
- Collect accelerometer sensor data
- Collect gyroscope sensor data
- Display live sensor readings during active monitoring
- Record abnormal movement data for later review
- Generate CSV files for completed sessions
- View previously recorded monitoring sessions
- Share saved CSV session files
- Delete saved session files
- Upload CSV session files to Firebase Storage
- Create a user account
- Log in and log out using Firebase Authentication
- Save and retrieve user profile information
- Support communication between the phone app and smartwatch app
- Navigate using bottom navigation and a drawer menu

## Technologies Used

- Android Studio
- Kotlin
- Java
- XML
- Firebase Authentication
- Firebase Realtime Database
- Firebase Storage
- Android Sensor Framework
- Gradle
- Bluetooth / Wi-Fi smartwatch communication

## System Requirements

To install and test this application, you will need:

- Android Studio installed
- Android device or Android emulator
- Android 8.0 or newer
- Internet connection for Firebase features
- USB debugging enabled if using a physical Android phone
- Optional smartwatch for wearable testing
- Phone and smartwatch paired through Bluetooth or Wi-Fi if testing wearable features
- Sufficient local storage for generated CSV session files

## Repository Structure

```text
MotionApp/
├── mobile/              # Mobile Android application
├── wear/                # Smartwatch application
├── gradle/              # Gradle wrapper files
├── build.gradle.kts     # Project-level Gradle configuration
├── settings.gradle.kts  # Project module settings
└── README.md
