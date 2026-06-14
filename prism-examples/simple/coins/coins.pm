dtmc

module die

	// local state
	s : [0..8] init 8;
	
	[] s=8 -> 0.5 : (s'=3) + 0.5 : (s'=7);

	[] s=0 -> 0.5 : (s'=1) + 0.5 : (s'=2); // c1
	[] s=1 -> (s'=3); // head1
	[] s=2 -> (s'=0); // tail1
	[] s=3 -> 0.5 : (s'=1) + 0.5 : (s'=2); // c2

	[] s=4 -> 0.5 : (s'=5) + 0.5 : (s'=6); // c3
	[] s=5 -> (s'=7); // head2
	[] s=6 -> (s'=4); // tail2
//	[] s=7 -> 0.5 : (s'=5) + 0.5 : (s'=6); // c4
	[] s=7 -> 0.5 + 0.001 : (s'=5) + 0.5 - 0.001: (s'=6); // c4 with epsilon = 0.001
	
endmodule

rewards "pos"
	s=1 | s=5 : 1;
endrewards

rewards "neg"
	s=2 | s=6 : 1;
endrewards