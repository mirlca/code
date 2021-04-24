
// Before instantiating MIRLCa, it is recommended that you define what path you will be storing the generated files.
// The slash at the end is very important.

p = "/Users/anna/Desktop/MIRLCa/"

// This line will create the directory if it doesn't exist yet:

File.mkdir(p);

// MIRLCa instantiation

a = MIRLCa.new(path: p)

// Start the recording of call&response dataset

a.startrecording


a.pitch(1, 440)
a.pitch(1, 220)


a.bpm(1, 120)
a.bpm(1, 120)

a.diss(

a.bpm(1, 120)


// Stop the recording of call&response dataset, give the name where you want to store in the Archive

a.stoprecording (\myTestingData12012021a)

// Just for testing purposes

a.loadrecording (\myTestingData12012021a)
a.loadrecording (\myTestingData12012021b)