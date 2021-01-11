
// Before instantiating MIRLCa, it is recommended that you define what path you will be storing the generated files.
// The slash at the end is very important.
// Please note: the directory should exist!

p = "/Users/anna/Desktop/MIRLCa/"

// MIRLCa instantiation

a = MIRLCa.new(path: p)

// Start the recording of call&response dataset

a.startrecording


a.pitch(1, 440)


a.pitch(1, 220)


// Stop the recording of call&response dataset

a.stoprecording