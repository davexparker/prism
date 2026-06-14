dtmc

module coin

	// local state
	s : [0..3] init 3;
	
	[] s=0 -> 0.5 : (s'=1) + 0.5 : (s'=2); // c1
	[] s=1 -> (s'=3); // head1
	[] s=2 -> (s'=0); // tail1
	[] s=3 -> 0.5 : (s'=1) + 0.5 : (s'=2); // c2

endmodule

rewards "pos"
	s=1 : 1;
endrewards

rewards "neg"
	s=2 : 1;
endrewards
