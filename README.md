# wgsl-fuzz

Technology for fuzzing tooling for the WebGPU shading language

## Generating variant shader jobs

Example: to generate 20 variants based on `samples/bubblesort_flag.wgsl` and put them in output directory `generated`, do:

```
mkdir generated
./scripts/runGenerator --originalShader samples/bubblesort_flag.wgsl --numVariants 20 --outputDir generated
# This should show a bunch of files including variant**.wgsl
ls generated
```

## Running a server instance

To run the server you first need to generate a `keystore.jks` file, which you should _not_ check in:

```
# Adapt SERVERNAME to the address of your server, e.g. localhost if you are running locally.
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365 -nodes -subj "/CN=SERVERNAME"

# This will prompt you to enter a password; remember it for the next step.
openssl pkcs12 -export -in cert.pem -inkey key.pem -out keystore.p12 -name alias

# Replace your_password with the password from the last step
keytool -importkeystore -deststorepass your_password -destkeypass your_password -destkeystore keystore.jks -srckeystore keystore.p12 -srcstoretype PKCS12 -srcstorepass your_password -alias alias

# Keep only keystore.jks
rm keystore.p12 key.pem cert.pem
```

To use port 443 (the browser default) for https, you will need to grant the `java` executable permission to bind to ports below 1024 without running as root. To do this, run the following as root or via sudo:

```
setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))
```

Now you can run the server as follows: (Note: if you did not give java permission to run on privileged ports then run using `--port 8443` which makes the port `8443` (or any other port you wish to use) instead of `443`)

```
# Replace admin_username and admin_password with the username and password that a client should use when issuing jobs via the server.
# Replace keystore_password with the password you used when creating keystore.jks.
WGSL_FUZZ_ADMIN_USERNAME=admin_username WGSL_FUZZ_ADMIN_PASSWORD=admin_password WGSL_FUZZ_KEYSTORE_PASSWORD=keystore_password ./scripts/runServer
```

# Connecting a worker to a server

First, check that WebGPU is enabled by the browser under test by going to:

```
https://webgpu.github.io/webgpu-samples/?sample=rotatingCube
```

If you see a pretty rotating cube then WebGPU is enabled! If not you may need to investigate how to enable it via an experimental setting.

Assuming WebGPU is enabled and you have a server running, connect a worker by going to:

```
# Replace yoursever with your actual server address, and WorkerName with the name you want your worker to have
https://yourserver?name=WorkerName
```

E.g. if your server is running locally and you want your worker to be called Billy, do:

```
https://localhost/?name=Billy
```

If you are running locally you may need to force your browser to load the page, as it cannot be verified as secure.

## Running a job via the server

To run the `samples/bubblesort_flag.wgsl` shader job via a server running at `yoursever`, use the following command:

```
mkdir outputs
# Replace admin_username and admin_password with the username and password you set up when launching the server.
# Replace yourserver with the address of your server.
# Add the --developerMode flag if you are using an insecure local server.
WGSL_FUZZ_ADMIN_USERNAME=admin_username WGSL_FUZZ_ADMIN_PASSWORD=admin_password ./scripts/runJobsViaServer --serverUrl https://yourserver --jobFile samples/bubblesort_flag.wgsl --workerName Billy --outputDir outputs --repetitions 3
```

If you are running an insecure local server at `localhost` then you will need to add the `--developerMode` flag, which allows connecting to a server whose certificate cannot be trusted.

Notice that the name of the worker to which the job should be issued is passed via the `--workerName` option. The `--repetitions` option specifies how many times the job should be executed; this is useful to allow checking for nondeterminism.

To execute all of the shader jobs in a particular directory, instead of the `--jobFile` argument use `--jobDir` and specify the name of the directory.

Either way, outputs from running the job(s) will be in the directory specified via `--outputDir`.

## Notes on the reducer

The reducer can be executed via the `scripts/reduceJobViaServer` script.

At the end of reduction, the simplest shader that was found will be in `simplest.wgsl`, with an annotated version including commentary about the transformations that remain in `simplest_annotated.wgsl`.

To help understand the transformations that could not be removed, the reducer will also try to save out a "one-step-simpler" shader that is not interesting - i.e. that does not trigger the bug. If available, this will be in `simpler_but_not_interesting.wgsl` and `simpler_but_not_interesting_annotated.wgsl`.

It can also be useful to compare `simplest.wgsl` or `simplest_annotated.wgsl` with the sample shader that was used to create the bug-inducing variant. The `scripts/parseAndPrettyPrint` script can be used to get the original sample shader into a pretty-printed form that helps when comparing against `simplest.wgsl`.

To perform comparisons, a visual diffing tool such as `meld` or `WinMerge` is recommended.
