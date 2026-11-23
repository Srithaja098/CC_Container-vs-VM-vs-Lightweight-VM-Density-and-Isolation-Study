================================================================================
CLOUDSIM 4.0 ISOLATION STUDY: DENSITY AND ISOLATION BENCHMARK SUITE
Container vs VM vs Lightweight-VM Density and Isolation Study
================================================================================
Team Allocation:
- Person 1: Core isolation model, IsolationProfile, VmSchedulerDemandShared, SimulationFactory
- Person 2: Experiment 1 (Density), Experiment 2 (Startup), Experiment 3 (Noisy Neighbour)
- Person 3: SimConfig layer, Main CLI/Console, Exp 4 (Mixed Tenancy), SweepRunner, Dashboard, Demo Runner
================================================================================

--------------------------------------------------------------------------------
1. ECLIPSE IDE: NUMBERED IMPORT AND RUN STEPS
--------------------------------------------------------------------------------

Step 1: Open Eclipse IDE (configured for Java 8 or higher).
Step 2: From the top menu bar, select:
        File -> Import...
Step 3: In the Import wizard, expand "General" and select:
        "Existing Projects into Workspace" -> Click Next.
Step 4: Click "Browse..." next to "Select root directory" and choose the workspace root folder:
        c:\SRITHAJA\CC_Container-vs-VM-vs-Lightweight-VM-Density-and-Isolation-Study
Step 5: Ensure the project is checked in the Projects list and click "Finish".
        (If importing as a standard Java Project, right-click the project -> Configure -> Convert to Java Project).
Step 6: Configure the Java Build Path and Classpath:
        a. Right-click the project -> Properties -> Java Build Path.
        b. In the "Source" tab, ensure "src" is added as the source folder.
        c. In the "Libraries" tab, click "Add JARs..." or "Add External JARs...".
        d. Add the two required dependency JARs located in the repository:
           - cloud-iso-study/lib/cloudsim-4.0.jar
           - cloud-iso-study/lib/commons-math3-3.6.1.jar
        e. In the "Order and Export" tab, ensure the two JARs are checked.
        f. Click "Apply and Close".
Step 7: Configure Run Configuration for the Main Console:
        a. In Package Explorer, expand "src" -> package "isolationstudy".
        b. Right-click "Main.java" -> Run As -> Run Configurations...
        c. Under "Java Application", select or create "Main".
        d. Under the "Main" tab, confirm:
           - Project: CC_Container-vs-VM-vs-Lightweight-VM-Density-and-Isolation-Study
           - Main class: isolationstudy.Main
        e. Under the "Arguments" tab:
           - Program arguments: (leave empty for interactive menu, or specify "--exp all")
           - VM arguments: -Xms256m -Xmx2048m
        f. Click "Run".
Step 8: Interacting with the Console:
        - View standard output in Eclipse's built-in "Console" tab.
        - Type menu numbers [1-9] into the console prompt and hit Enter.

--------------------------------------------------------------------------------
2. COMMAND LINE (CLI) NUMBERED RUN STEPS
--------------------------------------------------------------------------------

Option A: Windows Command Prompt / PowerShell
Step 1: Open Command Prompt (cmd.exe) or PowerShell and navigate to the project directory:
        cd C:\SRITHAJA\CC_Container-vs-VM-vs-Lightweight-VM-Density-and-Isolation-Study
Step 2: Run the automated console batch runner:
        run_console.bat
        (This automatically detects your JDK, compiles src/isolationstudy/*.java, and opens the interactive menu).
Step 3: Execute specific experiments or batch modes directly:
        - Run All Experiments:
          run_console.bat --exp all
        - Run a Single Experiment (e.g. Exp 1 Density or Exp 4 Mixed Tenancy):
          run_console.bat --exp 1
          run_console.bat --exp 4
        - Run a Parameter Sensitivity Sweep:
          run_console.bat --sweep 3 workload.neighbour_intensity 1.0 16.0 2.0
        - Generate the HTML Dashboard:
          run_console.bat --dashboard

Option B: Linux / macOS Terminal
Step 1: Open a terminal and navigate to the project root:
        cd /path/to/CC_Container-vs-VM-vs-Lightweight-VM-Density-and-Isolation-Study
Step 2: Ensure the shell script has execution permissions:
        chmod +x run_console.sh
Step 3: Run the interactive console menu:
        ./run_console.sh
Step 4: Run batch CLI commands:
        ./run_console.sh --exp all
        ./run_console.sh --dashboard

--------------------------------------------------------------------------------
3. EXPECTED OUTPUT AND BENCHMARK REFERENCE VALUES
--------------------------------------------------------------------------------

Configuration Parameters (from config/experiment.properties):
- Physical Host: 1 Host, 16 Cores @ 2500.0 MIPS, 64 GB RAM (65536 MB), 10 Gbps Bandwidth.
- Application Instance: 2 vCPUs @ 2500.0 MIPS, 256 MB Application RAM.
- Seed: 42 (Deterministic execution across all replicates).

Experiment 1: Host Density Limit (density.csv)
- Full VM (Pinned cores, 512 MB overhead -> 768 MB total):
  * Placed Instances per Host: 8 [FROM CSV] (Hardware limit: 16 cores / 2 vCPUs)
  * Rejection: 9th instance rejected.
- Container (Uncapped pool, 256 MB overhead -> 512 MB total):
  * Placed Instances per Host: 128 [FROM CSV] (Memory limit: 65536 MB / 512 MB)
- Lightweight VM (Capped vCPUs, 768 MB overhead -> 1024 MB total):
  * Placed Instances per Host: 64 [FROM CSV] (Memory limit: 65536 MB / 1024 MB)

Experiment 2: Fleet Startup Readiness (startup.csv)
- Startup Delays (Model Constants):
  * Full VM: 30.00 s (Heavy hypervisor and guest kernel boot)
  * Container: 2.00 s (Kernel cgroup/namespace allocation)
  * Lightweight VM: 3.80 s (MicroVM guest kernel boot)
- Fleet Readiness for 32-instance request:
  * Full VM: 8 created (host saturated), fleet ready at 30.00 s [FROM CSV]
  * Container: 32 created, fleet ready at 2.00 s [FROM CSV]
  * Lightweight VM: 32 created, fleet ready at 3.80 s [FROM CSV]

Experiment 3: Noisy-Neighbour Interference (noisy_neighbour.csv)
- 4 Victim Instances (100,000 MI cloudlets) + 1 High-Demand Neighbour (8x demand, 1,000,000 MI):
  * Full VM (PINNED): Baseline Slowdown: 1.00x [FROM CSV] | Noisy Slowdown: 1.00x [FROM CSV] | Ratio: 1.00x [FROM CSV]
  * Container (UNCAPPED): Baseline Slowdown: 1.00x [FROM CSV] | Noisy Slowdown: 1.50x [FROM CSV] | Ratio: 1.50x [FROM CSV]
    (Earlier reference target: x1.95; current model calibration delivers x1.50 under 4 victims and x2.00 under saturated pool).
  * Lightweight VM (CAPPED): Baseline Slowdown: 1.00x [FROM CSV] | Noisy Slowdown: 1.00x [FROM CSV] | Ratio: 1.00x [FROM CSV]

Experiment 4: Mixed-Tenancy Uncore Contention (mixed_tenancy.csv)
- Models memory-bus and I/O bus contention across co-located tenants:
  * Pure Full VM (8 instances): Mean Slowdown: 1.045x [FROM CSV] | IDS Score: 7.66 [FROM CSV]
  * Pure Lightweight VM (64 instances): Mean Slowdown: 11.000x [FROM CSV] | IDS Score: 5.82 [FROM CSV]
  * Pure Container (128 instances): Mean Slowdown: 35.058x [FROM CSV] | IDS Score: 3.65 [FROM CSV]
  * Mixed Balanced (2 FVM + 8 LVM + 32 CTR = 42 instances):
    - Full VM Slowdown: 1.181x [FROM CSV]
    - Lightweight VM Slowdown: 9.721x [FROM CSV]
    - Container Slowdown: 13.313x [FROM CSV]
    - Overall Slowdown: 12.051x [FROM CSV] | IDS Score: 3.49 [FROM CSV]

--------------------------------------------------------------------------------
4. TROUBLESHOOTING GUIDE
--------------------------------------------------------------------------------

Issue 1: "javac is not recognized as an internal or external command"
- Solution: Ensure a Java Development Kit (JDK 8 or higher, e.g. OpenJDK 8/17/21/24) is installed.
  Set the JAVA_HOME environment variable to your JDK directory, e.g.:
  set JAVA_HOME=C:\Program Files\Java\jdk-24
  set PATH=%JAVA_HOME%\bin;%PATH%

Issue 2: "package org.cloudbus.cloudsim does not exist" or ClassNotFoundException
- Solution: Confirm that "cloud-iso-study/lib/cloudsim-4.0.jar" and
  "cloud-iso-study/lib/commons-math3-3.6.1.jar" exist.
  When compiling manually, supply the classpath:
  javac -cp "cloud-iso-study\lib\cloudsim-4.0.jar;cloud-iso-study\lib\commons-math3-3.6.1.jar" -d target\classes src\isolationstudy\*.java

Issue 3: "OutOfMemoryError: Java heap space" during large sweep runs
- Solution: Increase the JVM heap size when invoking Java by passing the -Xmx flag:
  java -Xms512m -Xmx4096m -cp "target/classes;..." isolationstudy.Main --exp all

Issue 4: Offline HTML Dashboard does not render charts
- Solution: The dashboard uses pure HTML5 Canvas vector graphics and inline JavaScript with ZERO external
  CDN dependencies. Simply double-click "dashboard.html" or "code/results/dashboard.html" to open it in any
  modern browser (Chrome, Edge, Firefox, Safari). JavaScript execution must be enabled.

Issue 5: CloudSim static entity reset errors across successive runs
- Solution: CloudSim 4.0 stores simulation entities and clock in static variables. SimulationFactory.build()
  automatically invokes CloudSim.init() with a fresh calendar and resets all static state before every
  scenario execution. Do not execute concurrent threads against the CloudSim core.

--------------------------------------------------------------------------------
5. ASSUMPTIONS
--------------------------------------------------------------------------------
1. Host Architecture: Homogeneous physical hosts with identical core speed (2500 MIPS) and memory.
2. CloudSim Version: Strictly CloudSim 4.0 API; no external third-party scheduling frameworks.
3. Uncore Contention: Memory-bandwidth and I/O bus contention are modeled as scaling factors on effective
   service rates based on active concurrent tenant load and tenant isolation boundaries.
4. Determinism: Seed 42 is fixed to guarantee exact reproducibility across all five repeat runs.
================================================================================

