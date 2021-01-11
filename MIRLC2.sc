// @new users, control the following customizable vars:
// - Freesound.token = "<your_api_key>"
// - path: replace current directory to your own directory to store downloaded sounds and record the text file with the credits, or change it to "/tmp/"
// - debugging: True/False


MIRLCRep2 {

    classvar <>server;
    classvar <>file;
    classvar <>date;
    classvar <>debugging;
    var metadata, buffers, synths, effects, <translation;
    var snd, preview, buf, target, sequential, granular;
    var poolsizeold, index, keys, counter, sndid, numsnds, size, rndnums;
    var window, viewoscil;
    var backendClass;
    var databaseSize;
    var directoryPath;
	var g0, g1;
	var b0, b1, b2;
	var fxlpf;
	var effectson;

	var maxvol =0.2;// 0.07; // 0.2; // audio samples

    *new {|backend = 0, dbSize = 478456, path = "Platform.defaultTempDir"|
        ^super.new.init(backend, dbSize, path)
    }

    init {|backend, dbSize, path|
        server = Server.local;
        server.boot;
        metadata = Dictionary.new;
        buffers = Dictionary.new;
        synths = Dictionary.new;
		effects = Dictionary.new;
        translation = Dictionary.new;
        debugging = True;
        poolsizeold = 0;
        counter = 0;
        sequential = False;
		granular = False;
        databaseSize = dbSize;
        directoryPath = path;
		effectson = 0;


        if(backend == 0){
            backendClass = FSSound;
            Freesound.authType = "token"; // default, only needed if you changed it
            Freesound.token="<your_api_key>"; // important!: change it to own API key token
        }{
            backendClass = FLSound;
        };

        date = Date.getDate;

		file = File(directoryPath.standardizePath ++ date.stamp ++ "_credits" ++ ".txt","w");
		file.write("Sound samples used:\n");

       this.argstranslate;

		server.waitForBoot {

			g0 = Group.new(server);
			g1 = Group.after(g0);
			b0 =  Bus.audio(server);
			//b1 =  Bus.audio(server);
			//b2 =  Bus.audio(server);

		SynthDef(\synth_mono_fs, {
            |bufnum, buf, amp = 1, out = 0, rate = 1, da = 0, loop = 1, trig = 0, gate = 1, atk=0.1, sus=1, rel=1.0|
            var sig, env;
            sig = PlayBuf.ar(1, bufnum, BufRateScale.kr(buf) * rate,  doneAction: da, loop: loop, trigger: trig);
			env = EnvGen.ar(Env.asr(atk,sus,rel), gate, doneAction:2);
			sig = sig * amp * env;
			Out.ar(out, sig!2);
        }).add;


		// Filters

		SynthDef(\lowpass_filter, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = LPF.ar(source);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\highpass_filter, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = HPF.ar(source);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\bandpass_filter, {
			arg ibs, obs, amp=1,
			atk=0.1, sus=1, rel=0.2, crv=2, gate=1, freq;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = BPF.ar(source, freq); //%TODO
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\bitcrush, {
			arg ibs, obs, amp=0.4,
			atk=0.02, sus=1, rel=0.1, crv=2, gate=1,
			rate=44100, bit=8;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = Decimator.ar(source, rate, bit);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef(\rverb, {
			arg ibs, obs, amp=1,
			atk=0.02, sus=1, rel=0.1, crv=2, gate=1,
			mix = 0.5 /*dry/wet balance 0..1*/,
			rm = 0.5 /*room size 0..1*/,
			damp=0/*reverb HF damp 0..1*/;
			var source, sig, env;
			env = EnvGen.ar(Env.asr(atk, sus, rel, crv), gate);
			source = In.ar([ibs, ibs]);
			sig = FreeVerb.ar(source, mix, rm, damp);
			Out.ar(obs, sig*env*amp);
		}).add;

		SynthDef.new(\dlay, {
			arg ibs=0, obs=0, amp=0.8, deltime = 0.3, mix = (-0.5), decay=3, delHz = 0.25, delMin = 0.1, delMax = 0.4;
			var sig, delay;
			sig = In.ar([ibs, ibs]);
			delay = CombL.ar(sig, 0.5, SinOsc.kr([delHz, delHz*0.9]).exprange(delMin, delMax), decay);
			sig = XFade2.ar(sig, delay, mix) * amp;
			Out.ar(obs, sig);
		}).add;

       //this.scope;

		};




    } //--//


    //---------------------------------------------------//
    //SOUND GROUP MANAGEMENT (PRIVATE FUNCTIONS)
    //---------------------------------------------------//
    // FUNCTIONS: load, loadmetadata


    //------------------//
    // RETRIEVE SOUNDS
    //------------------//
    // This function manages the dictionary metadata (sounds with fs info) and loads the new sounds
    // This function is in charge of the storage of a new group of sounds by managing the right index number when calling the function load() for each new sound from a dictionary of json info and resetting the counter.
    loadmetadata { |totalsnds = 1|
        totalsnds.do ({ |index|
            this.loadsounds(metadata, (index + poolsizeold) );
        });
        poolsizeold = metadata.size;
        counter = 0; // used in random
        this.printmetadata;
    }

    //------------------//
    // LOAD SOUNDS
    //------------------//
    // This function parses the Freesound information of each sound and converts it to the SuperCollider language, storing all the info in two dictionaries (buffers and Synths). The result is a sound that plays once is correctly stored in the synths dictionary.
    loadsounds { |dict, index|
        dict[index].retrievePreview(directoryPath, {
			/*("previewFilename").postln;
			dict[index].previewFilename.postln;*/
            buf = Buffer.readChannel(server, directoryPath ++ dict[index].previewFilename,
                channels: [0],
                action: { |buf|
                    if (sequential == False,
                        {
							if (effectson == 0,
								{
									synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buf, \bufnum, buf.bufnum, \loop, 1, \amp, maxvol], g0) ); },
								{
										synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buf, \bufnum, buf.bufnum, \loop, 1, \amp, maxvol, \out, b0]) );
								});

                        },
                        {
                            // do nothing if in sequential mode
                    });
                    buffers.add(index -> buf);
            });
        });
    }

    //---------------------------------------------------//
    //QUERIES TO SEED A POOL OF SOUNDS (TEXT, CONTENT)
    //---------------------------------------------------//
    // FUNCTIONS: random, tag, content


    //------------------//
    // GET SOUND BY ID
    //------------------//
    // This function can be used as a standalone public function to get [1..n] sounds by ID, and it is also used as a private function by random, tag, similar, filter, content to get sounds
    // params: id, size
    id { |id = 31362, size = 1|

		// If (size > 1 && sound exists in the folder) // in the future store the metadata as well and so then if (size > 1)
		// just copy the info from previous
		// else:
        backendClass.getSound(id,
            { |f|
                //available metadata: "id","url","name","tags","description","geotag","created","license","type","channels","filesize""bitrate","bitdepth","duration","samplerate","username","Jovica","pack","pack_name","download","bookmark","previews","preview-lq-mp3","preview-hq-ogg","preview-hq-mp3","images","num_downloads","avg_rating","num_ratings","rate":,"comments","num_comments","comment","similar_sounds","analysis","analysis_frames","analysis_stats"
                snd = f;
                index = metadata.size;
                file.write(snd["name"] + " by " + snd["username"] + snd["url"] + "\n");

				/*"name".postln;
				snd["name"].postln;*/

                metadata.add(index -> f);

                if (size == 1) {
                    this.loadmetadata(size);
                    //this.printmetadata;
                }{ // size > 1
                    if ( (metadata.size - poolsizeold) == size, // need to wait until asynchronous call is ready! once all sounds are added in the dictionary, they can be retrieved
                        {
                            this.loadmetadata(size);
                            //this.printmetadata;
                        }
                    );
                }
        } );
    } //--//

    //------------------//
    // QUERY BY RANDOM
    //------------------//
    // This function gets [1..n] sounds by random, and plays them
    random { |size = 1|

        // if ( debugging == True, {postln("Sounds selected by random: " ++ size);} );
        sndid = rrand (1, databaseSize);
        backendClass.getSound ( sndid,
            { |f|

                snd = f;

                if ( snd["detail"] == nil,
                    {
                        if ( debugging == True, {
                            postln("potential sound candidate: ");
                            snd["name"].postln;
                        });
                        postln("counter value is: " + counter);
                        counter = counter + 1;
                        if (size == 1,
                            {
                                this.id(sndid, size);
                            },
                            {//size > 1
                                //this.id(sndid, size);
                                postln("group size is greater than 1");
                                postln("( counter - size ): " ++ ( counter - size ));
                                if ( counter <= size ,
                                    //if ( (metadata.size - poolsizeold - size) < 0 ,
                                    {
                                        this.id(sndid, size);
                                        if ( counter < size, { this.random(size); } );
                                    }
                                );
                            }
                        );
                    },
                    {
                        if ( debugging == True, {"SoundID does not exist".postln;} );
                        this.random(size);
                } );
        } );
    } //--//

    //------------------//
    // QUERY BY TAG
    //------------------//
    // This function gets [1..n] sounds by one defined tag, and plays them
    tag { |tag = "noise", size = 1|

        if ( debugging == True, {
            postln("Sounds selected by tag: " ++ size);
        });
        backendClass.textSearch(query: tag, params: ('page': 1),
            action: { |p|
                size.do { |index|
                    snd = p[index];
                    postln("found sound by tag, id: " ++ snd["id"] ++ "name: " ++ snd["name"]);
					while (
						{this.sndidexist(snd.id) == 1},
						{
							postln ("repeated sound, getting another sound...");
							index = index + 1 + size;
							snd = p[index];
					});
                this.id(snd.id, 1); // so that each sound is loaded & directly played
                }
		    });
    } //--//


    //------------------//
    // QUERY BY CONTENT
    //------------------//
    // This function gets [1..n] sounds by one defined feature and fx, and plays them
    content { |size = 1, feature = 'dur', fvalue = 1, fx = 'conf', fxvalue = 'bypass' |
        var fconcat, fxconcat;
        if (feature != 'id',
          { fconcat = this.gettranslation(feature.asSymbol)++fvalue; },
          { fconcat = fvalue });
        fxconcat = this.gettranslation(fx.asSymbol) ++ this.gettranslation(fxvalue);
        backendClass.contentSearch(
            target: fconcat,
            filter: fxconcat,
            params: ('page':1),
            action: {|p|
                size.do { |index|
                    snd = p[index];
                    //check if snd.id already exists, if so, take next sound
                    if (metadata.size > 0,
                        {
                            while ( {this.sndidexist(snd.id) == 1},
                                {
                                    index = index + size;
                                    snd = p[index];
                                    postln ("repeated sound, getting another sound...");
                            });
                    });
                    this.id(snd.id, 1);  // so that each sound is loaded directly played;
                }
            }
        );
    } //--//

	pitch {|size = 1, fvalue = 440, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'pitch', fvalue, fx, fxvalue);

	}

	bpm {|size = 1, fvalue = 60, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'bpm', fvalue, fx, fxvalue);

	}

	dur {|size = 1, fvalue = 10, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'dur', fvalue, fx, fxvalue);

	}

	diss {|size = 1, fvalue = 1.0, fx = 'conf', fxvalue = 'lo'|

		this.content(size, 'dissonance', fvalue, fx, fxvalue);

	}


    //---------------------------------------------------//
    // QUERIES TO CONTINUE ADDING SOUNDS (QUERY BY EXAMPLE)
    //---------------------------------------------------//
    // FUNCTIONS: similar, filter

    //------------------//
    // SIMILAR SOUNDS
    //------------------//
    // This function gets [1..n] similar sounds from a target sound, usually the first sound from the dictionary
    similar { | targetnumsnd = 0, size = 1 |

        target = metadata[targetnumsnd];  // before: metadata[targetnumsnd - 1];

        target.getSimilar(
            action: { |p|
                size.do { |index|
                    snd = p[index+1]; // to avoid retrieving the same sound of the query
                    //check if snd.id already exists, if so, take next sound
                    if (metadata.size > 0,
                        {
                            while ( {this.sndidexist(snd.id) == 1},
                                {
                                    index = index + 1 + size;
                                    snd = p[index];
                                    postln ("repeated sound, getting another sound...");
                            });
                    });
                    this.id(snd.id, 1); // so that each sound is loaded directly played
                }
        });

    } //--//

	similarauto { |targetnumsnd = 0, size = 3, tempo = 30|

		var counter = size;
		var offset = targetnumsnd;
		var t = TempoClock.new;

		t.sched(tempo, {
			//"hello".postln; // still 3
			//n.postln;
			"getting a similar sound (auto mode)...".postln;
			this.similar(offset);
			counter = counter - 1;
			offset = offset + 1;
			if (counter <= 0,
				{nil},
				{tempo}
			);
		});

	}	//--//

		sameidauto { |id = 0, size = 3, tempo = 30|

		var counter = size;
		var t = TempoClock.new;

		t.sched(tempo, {
			"getting same sound (auto mode)...".postln;
			this.id(id);
			counter = counter - 1;
			if (counter <= 0,
				{nil},
				{tempo}
			);
		});

	}	//--//

    //------------------//
    // SIMILAR BY RANGE
    //------------------//
    // This function gets [1..n] similar sounds from a target sound filtered by a fx
    filter { |targetnumsnd = 0, size = 1, fx = 'conf', fxvalue = 'bypass' |

        var  fxconcat;
        fxconcat = this.gettranslation(fx.asSymbol) ++ this.gettranslation(fxvalue);

        sndid = metadata[targetnumsnd].id; // before: metadata[targetnumsnd - 1].id

        backendClass.contentSearch(
            target: sndid,
            filter: fxconcat,
            params: ('page':1),
            action: {|p|
                size.do { |index|
                    snd = p[index];
                    //snd.name.postln;
                    //check if snd.id already exists, if so, take next sound
                    if (metadata.size > 0,
                        {
                            while ( {this.sndidexist(snd.id) == 1},
                                {
                                    index = index + size;
                                    snd = p[index];
                                    postln ("repeated sound, getting another sound...");
                            });
                    });
                    this.id(snd.id, 1); // so that each sound is loaded directly played
                }
            }
        );
    } //--//

    //---------------------------------------------------//
    // ANALYZING SOUNDS / AIDING METHODS
    //---------------------------------------------------//
    // FUNCTIONS:

    //------------------//
    // ANALYZE
    //------------------//
    // This function retrieves all content-based descriptors listed in the Analysis Descriptor Documentation from the FreeSound API: "https://www.freesound.org/docs/api/analysis_docs.html#analysis-docs"
    // The result can be filtered using the descriptors request parameter passing a list of comma separated descriptor names chosen from the available descriptors e.g. 'descriptors=lowlevel.mfcc,rhythm.bpm'
    analyze {|descriptors, action|

        metadata.size.do( { |index|
            metadata[index].getAnalysis( descriptors, action, {|val|
                val.postln;
            }, true)
        });

    }//--//

	 //------------------//
    // WHAT ID
    //------------------//
    whatid { |feature = "id" |
        metadata.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id);
        });
    }//--//

	 //------------------//
    // WHAT MASTER VOLUME
    //------------------//
    whatvol {
        postln("[" ++ maxvol);
    }//--//


    //------------------//
    // WHAT PITCH
    //------------------//
    whatpitch { |feature = "lowlevel.pitch.mean" |
        this.analyze(feature);
    }//--//

    //------------------//
    // WHAT KEY
    //------------------//
    whatkey { |feature = "tonal.key_key" |
        this.analyze(feature);
    }//--//

    //------------------//
    // WHAT BPM
    //------------------//
    whatbpm { |feature = "rhythm.bpm" |
        this.analyze(feature);
    }//--//

    //------------------//
    // WHAT DURATION (sec)
    //------------------//
    whatdur { |feature = "sfx.duration" |
        this.analyze(feature);
    }//--//


    //---------------------------------------------------//
    // FUNCTIONS FOR LIVE CODING WITH THE SOUNDS
    //---------------------------------------------------//
    // FUNCTIONS: play, sequence, sequencemachine (private), parallel, stop, solo, solo all, mute, mute all, free, free all

    //------------------//
    // PLAY
    //------------------//
    // This function plays the first sound of the class Dictionary collection play(1), otherwise it plays all
    play {|rate = 1|
        size = synths.size;
		"THE SIZE OF SYNTHS IS "+synths.size;
		//if( granular == True, { granular = False });
        size.do( { |index|
			if( effectson == 0,
				{
					synths[index].set(\amp, maxvol, \rate, rate, \out, 0);
				},
				{
					synths[index].set(\amp, maxvol, \rate, rate, \out, b0);
				}
			);

            this.printsynth(index);
        });
    } //--//

	// Need to deactivate it calling at playauto(0)

	playauto { |times = 4, tempo = 30|

		var counter = times;
		var bool = 1;
		var speed = 1;

		var t = TempoClock.new;

		t.sched(tempo, {

			if (counter >= 0,
				{
					if ( bool == 1,
						{
						speed = 1.0.rand.postln*(-1);
						this.play(speed);
						"Play backwards <<".postln;
						bool = 0;
						},
						{
						speed = 1.0.rand.postln;
						this.play(speed);
						"Play forwards >>".postln;
						bool = 1;
						}
					);
					counter = counter - 1;
					tempo;
				},
				{
					this.play(1);
					"Done playauto".postln;
					nil;
				}
			);
		});

	}	//--//

	playautodown { |start = 1, end = 0, times = 5, tempo = 10|

		var period = abs(end - start) / times;//0.2
		var counter = start - period; //1
		var speed = start - period;

		var t = TempoClock.new;

		t.sched(tempo, {

			if (counter >= 0.01,
				{
					this.play(speed);
					"playing at: "+speed.postln;
					counter = counter - period;
					speed = speed - period;
					tempo;
				},
				{
					this.stop;
					"Done playdown".postln;
					nil;
				}
			);
		});

	}	//--//

	autochopped { |times = 4, tempo = 1|

		//loop times
		//this.playauto(RAND)

		var counter = times;
		var bool = 1;
		var speed = 1;

		var t = TempoClock.new;

		//tempo = tempo*[1,2,3,4,5].choose

		t.sched(tempo, {
			"tempo: "+tempo.postln;
			if (counter >= 0,
				{
					if ( bool == 1,
						{
						speed = 1.0.rand.postln*(-1);
						this.play(speed);
						"Play backwards <<".postln;
						bool = 0;
						},
						{
						speed = 1.0.rand.postln;
						this.play(speed);
						"Play forwards >>".postln;
						bool = 1;
						}
					);
					counter = counter - 1;
					tempo;
				},
				{
					this.play(1);
					"Done playauto";
					nil;
				}
			);
		});

	} //--//


	//------------------//
    // LPF
    //------------------//
    // This function bypasses the effects
	bypass {
		effectson = 0;
		this.play;
	}//--//

	//------------------//
    // LPF
    //------------------//
    // This function applies a lowpass filter
    lowpf {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\lowpass_filter, [\obs, 0, \ibs, b0], g1));


    } //--//

	//------------------//
    // HPF
    //------------------//
    // This function applies a highpass filter
    highpf {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

        effects.add (0 -> Synth.new(\highpass_filter, [\obs, 0, \ibs, b0], g1));

    } //--//

	//------------------//
    // BPF
    //------------------//
    // This function applies a bandpass filter
   bandpf { |freq = 440|

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

        effects.add (0 -> Synth.new(\bandpass_filter, [\freq, freq, \obs, 0, \ibs, b0], g1));

    } //--//

	//------------------//
    // BitCrush
    //------------------//
    // This function applies a bitcrush filter
    bitcrush {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\bitcrush, [\obs, 0, \ibs, b0], g1));

    } //--//


	//------------------//
    // Reverb
    //------------------//
    // This function applies a reverb
    reverb {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);
		effects.add (0 -> Synth.new(\rverb, [\obs, 0, \ibs, b0], g1));



    } //--//


	//------------------//
    // Delay
    //------------------//
    // This function applies a reverb
    delay {

		effectson = 1;
        size = synths.size;
        size.do( { |index|
            synths[index].set(\out, b0);
            this.printsynth(index);
        });

		if ( effects.size>0,
			{
				effects[0].free;
			}
		);

		effects.add (0 -> Synth.new(\dlay, [\obs, 0, \ibs, b0], g1));
    } //--//



    //------------------//
    // SEQUENCE
    //------------------//
    // This function plays sounds sequentially, one after the other
    sequence {
        "--- sequence mode".postln;
		if ( sequential == False,
			{
				"STATE 3: from parallel to sequence".postln; sequential = True;
				sequential.postln;
				"change behavior from PARALLEL to SEQUENCE".postln;
				// removing all the sounds except for the first
				 index = 0;
                synths.size.do{ |b|
                    if( b>0,
                        {
                            this.free(b);
                        }
                    );
                };
                synths[index].set(\loop, 0, \da, 2, \amp, maxvol);
                this.printsynth(index);
                synths[index].onFree {
					if ( sequential == True, {
						"--- sequencemachine mode".postln;
						this.sequencemachine(index);
					});
                };
				//
		}, {
				"STATE 4: from sequence to sequence".postln; sequential.postln;
				//sequential = True;
				"keep behavior SEQUENCE".postln;
		});
    } //--//

    //------------------//
    // SEQUENCE MACHINE (PRIVATE)
    //------------------//
    // This function is private and makes sure to play sounds sequentially
    sequencemachine { |index = 0|

		if ( (index+1) < buffers.size, {index = index + 1}, {index = 0} );
		"index value in sequence machine: ".postln;
		index.postln;
		if(effectson==0,
			{
				synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 0, \da, 2, \amp, maxvol], g0) );
			},
			{
				synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 0, \da, 2, \amp, maxvol, \out, b0]) );
			}
		);

        this.printsynth(index);
        synths[index].onFree {
            if (sequential == True,
            {
			"inside recursion".postln;
            this.sequencemachine(index);
            } );
        };
    } //--//

    //------------------//
    // PARALLEL
    //------------------//
    // This function plays sounds in parallel, all of them looping at the same time. If it comes from sequential, it will start once the sound that is playing in the sequential state ends.
    parallel {
        "--- parallel mode".postln;

			if ( sequential == True,
			{
				"STATE 1: from sequence to parallel".postln;
				sequential = False;
				sequential.postln;
				"change behavior from SEQUENCE to PARALLEL".postln;
				this.parallelmachine;
				/*if ( synths != nil, {
					"change behavior from SEQUENCE to PARALLEL".postln;
						this.parallelmachine;
					}, {
					"not deleting".postln;
					"change behavior from SEQUENCE to PARALLEL".postln;
					this.parallelmachine;
				});*/

		}, {
				"STATE 2: from parallel to parallel".postln; sequential.postln;
				//sequential == False;
				"keep behavior PARALLEL". postln;
		});


    }

	//------------------//
    // PARALLEL MACHINE (PRIVATE)
    //------------------//
    // This function is private and makes sure to play sounds in parallel
    parallelmachine {

        size = buffers.size;
        size.do( { |index|
			if( effectson==0,
				{
					synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 1, \da, 0, \amp, maxvol], g0) );
				},
				{
					synths.add (index -> Synth.new(\synth_mono_fs, [\buf, buffers[index], \bufnum, buffers[index].bufnum, \loop, 1, \da, 0, \amp, maxvol, \out, b0]) );
				}
			);
        });
        this.printsynths;
		this.play;

    } //--//



    //------------------//
    // VOLUME
    //------------------//
    // This function changes the volume of the whole group from 0 to 1
    volume {|vol = 0.2|
        size = synths.size;
        size.do( { |index|
            synths[index].set(\amp, vol);
        });
    } //--//

    //------------------//
    // STOP
    //------------------//
    // This function stops the first sound of the class Dictionary collection play(1), otherwise it plays all
    stop {
        size = synths.size;
        size.do( { |index|
            synths[index].set(\amp, 0);
        });
    } //--//

    //------------------//
    // FADE OUT
    //------------------//
	// This function fades out all synths with a smooth fade out

    fadeout {|release = 1.0|
		sequential = False; // to avoid inconsistencies
		postln("Number of sounds fading out: " ++ synths.size);
		synths.size.do( { |index|
			synths[index].set(\gate, 0, \rel, release, \da, 2);
		});
    } //--//


    //------------------//
    // SOLO
    //------------------//
    // This function..
	  solo { |targetnumsnd=0|
        synths.size.do( { |index|
            if (index == (targetnumsnd), // before: (index == (targetnumsnd-1)
                {synths[index].set(\amp, maxvol)},
                {synths[index].set(\amp, 0)}
            );
        });
    } //--//

    //------------------//
    // MUTE
    //------------------//
    // This function..
    mute { |targetnumsnd=0|
        synths[targetnumsnd].set(\amp, 0); // before: synths[targetnumsnd-1].set(\amp, 0);
    } //--//

    //------------------//
    // MUTE ALL
    //------------------//
    // This function..
    muteall { |targetnumsnd=0|
        synths.size.do( { |index|
            synths[index].set(\amp, 0);
        });
    } //--//


	//------------------//
	// FREE ALL
	//------------------//
	// This function...
	// private function
	freeall {
		synths.size.do( { |index|
			synths[index].free;
		});
	} //--//

    // private function
    free {|index|
        synths[index].free;
    }

    //---------------------------------------------------//
    // UTILS
    //---------------------------------------------------//
    // FUNCTIONS: sndexist, argstranslate, cmdperiod

    //------------------//
    // DOES A SOUND EXIST
    //------------------//
    // This function returns whether the sound is already in the metadata dictionary or not
    sndidexist { |id|
        var index;
        var mdsize = metadata.size;

        block( { |break|
            mdsize.do( { |index|
                //postln(metadata[0].id == id);
                //postln(index);
                if ( metadata[index].id == id,
                    { ^1 }
                );
            });
            {^0};
        });
    } //--//

    //------------------//
    // TRANSLATE TO FS ARGS
    //------------------//
    // This function maps from shorter arguments to the ones expected by the FreeSound quark
    argstranslate {
        //Features
        translation.add(\pitch -> ".lowlevel.pitch.mean:");
        translation.add(\dur -> ".sfx.duration:");
        translation.add(\dissonance -> ".lowlevel.dissonance.mean:");
        translation.add(\bpm -> ".rhythm.bpm:");
        //Filters
        translation.add(\key -> "tonal.key_key:");
        translation.add(\scale -> "tonal.key_scale:");
        translation.add(\conf -> ".lowlevel.pitch_instantaneous_confidence.mean:");
        translation.add(\mfcc0 -> "lowlevel.mfcc.mean[0]:");
        translation.add(\mfcc1 -> "lowlevel.mfcc.mean[1]:");
        translation.add(\mfcc4 -> "lowlevel.mfcc.mean[1]:");
        //Filter values
        translation.add(\Asharp-> "\"ASharp\"");
        translation.add(\A-> "\"A\"");
        translation.add(\B-> "\"B\"");
        translation.add(\C-> "\"C\"");
        translation.add(\D-> "\"D\"");
        translation.add(\E-> "\"E\"");
        translation.add(\F-> "\"F\"");
        translation.add(\G-> "\"G\"");
        translation.add(\major -> "\"major\"".asString);
        translation.add(\minor -> "\"minor\"".asString);
        translation.add(\hi -> "[0.8 TO 1]");
        translation.add(\lo -> "[0 TO 0.2]");
        translation.add(\bypass -> "[0 TO 1]");
        translation.add(\1720 -> "[17 TO 20]");
        translation.add(\2040 -> "[20 TO 40]");
        translation.add(\neg -> "[-1124 TO -1121]");
        //translation.add(\diss -> "lowlevel.dissonance.mean:"); // 0 -> consonant, 1-> dissonant [0.3-0.5]
        //translation.add(\voiced -> "lowlevel.spectral_entropy.mean:"); // [2-10]

    } //--//


    //------------------//
    // GET TRANSLATION
    //------------------//
    // This function translates a parameter only if it exists in the dictionary translation

    gettranslation{|key|
    		if (translation.includesKey(key)){
    			^translation[key];
    		}{
    			^key;
    		}

    	}

    //------------------//
    // CMD PERIOD
    //------------------//
    // This function is activated when stopping the code / recompiling / etc.
    cmdPeriod {
        file.close;
        currentEnvironment.clear;
    } //--//


    //---------------------------------------------------//
    // VISUALIZATION, PRINTING
    //---------------------------------------------------//
    // FUNCTIONS: scope, plotserver, printmedata, printsynths, printbuffers, printall

    //------------------//
    // SCOPE
    //------------------//
    // This function...

    scope {
        /*window = Window.new("MIRLC scope", Rect(200, 200, 1200, 500));
        window.view.decorator = FlowLayout(window.view.bounds);
        viewoscil = Stethoscope.new(server, view:window.view);
        window.onClose = { viewoscil.free }; // don't forget this
        window.front;*/
        Stethoscope.new(server);
    } //--//

    //------------------//
    // PLOT SERVER
    //------------------//
    // This function...
    plotserver {
		//server.scope;
		server.plotTree;
		server.queryAllNodes;
    } //--//

    //------------------//
    // PRINT METADATA
    //------------------//
    // This function prints the FS metadata information for all downloaded sounds
    printmetadata {
        metadata.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration);
        });
    } //--//

	// DUPLICATED from "printmetadata". TODO: merge it
    //------------------//
    // PRINT METADATA
    //------------------//
    // This function prints the FS metadata information for all downloaded sounds
    info {
        metadata.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration);
        });
    } //--//

    //------------------//
    // PRINT BUFFERS
    //------------------//
    // This function prints the buffers information and associated FS metadata information for all downloaded sounds
    printbuffers {
        buffers.size.do ({ |index|
            postln("[" ++ index ++ "]: " ++ buffers[index] ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username);
        });
    } //--//

    //------------------//
    // PRINT SYNTHS
    //------------------//
    // This function prints the synths information and associated FS metadata information for all the active sounds
    printsynths {
        synths.size.do ({ |index|
            //postln("[" ++ index ++ "]: " ++ synths[index] ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username );
            postln("now playing..." ++ "[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration ++ $\n ++ synths[index] );
        });
    } //--//

    //------------------//
    // PRINT SYNTH
    //------------------//
    // This function prints the synth information and associated FS metadata information of the current active sound
    printsynth { |index|
        postln("now playing..." ++ "[" ++ index ++ "]: " ++ "id: " ++ metadata[index].id ++ " name: " ++ metadata[index].name ++ " by: " ++ metadata[index].username ++ " dur: " ++ metadata[index].duration ++ $\n ++ synths[index] );
    } //--//

    //------------------//
    // PRINT ALL (METADATA, BUFFERS, SYNTHS)
    //------------------//
    // This function...
    printall {
        postln("FS metadata dictionary: ");
        this.printmetadata;
        postln("buffers dictionary: ");
        this.printbuffers;
        postln("synths dictionary: ");
        this.printsynths;
    } //--//


}



