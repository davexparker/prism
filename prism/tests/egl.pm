// randomized protocol for signing contracts Even, Goldreich and Lempel

dtmc

// we now let B to makes his/her choices based on what he/she knows 
// to do this I have added non-determinism to the previous version
// and changed the modules so that only "B's view" is visible
// then reveal the values when B thinks he has an advantage

// to model the non-deterministic behaviour of corrupt party (party B)
// we have a set of possible initial states corresponding to what messages
// he/she tries to over hear when sending - we could do this with nondeterminism 
// but it will just make the model less structured and B has to make the choices 
// at the start anyway since B's view at this point should tell him nothing
// (we use the new construct init...endinit to specify the set of initial states)

// note that certain variables that belong to a party appear in the other party's module
// as this leads to a more structured model - without this PRISM runs out of memory

// note we have included the case when B stops if he/she thinks that the protocol has reached 
// a state where he/she has an advantage

const int N; // number of pairs of secrets the party sends
const int L; // number of bits in each secret

module counter
	
	i : [1..L]; // counter for current bit to be send (used in phases 2 and 3)
	n : [0..max(N-1,1)]; // counter as parties send N messages in a row
	phase : [1..5]; // phase of the protocol
	party : [1..2]; // which party moves
	// 1 first phase of the protocol (sending messages of the form OT(.,.,.,.)
	// 2 and 3 - second phase of the protocol (sending secretes 1..n and n+1..2n respectively)
	// 4 finished the protocol
	
	// FIRST PHASE
	[receiveB] phase=1 & party=1 -> (party'=2); // first A sends a message then B does
	[receiveA] phase=1 & party=2 & n<N-1 -> (party'=1) & (n'=n+1); // after B sends a message we move onto the next message
	[receiveA] phase=1 & party=2 & n=N-1 -> (party'=1) & (phase'=2) & (n'=0); // B has sent his final message - move to next phase
	// SECOND AND THIRD PHASES
	// when A sends
	[receiveB] ((phase)>=(2)&(phase)<=(3))& party=1 & n=0-> (party'=2); // A transmits bth bit of secrets 1..N or N=1..2N
	[receiveA] ((phase)>=(2)&(phase)<=(3))& party=2 & n<N-1-> (n'=n+1); // A transmits bth bit of secrets 1..N or N=1..2N
	[receiveA] ((phase)>=(2)&(phase)<=(3))& party=2 & n=N-1 -> (party'=1) & (n'=1); // finished for party A now move to party B
	// when A sends
	[receiveB] ((phase)>=(2)&(phase)<=(3))& party=1 & n<N-1 & n>0 -> (n'=n+1); // B transmits bth bit of secrets 1..N or N=1..2N
	[receiveB] ((phase)>=(2)&(phase)<=(3))& party=1 & n=N-1 & i<L -> (party'=1) & (n'=0) & (i'=i+1); // finished for party B move to next bit
	[receiveB] phase=2 & party=1 & n=N-1 & i=L -> (phase'=3) & (party'=1) & (n'=0) & (i'=1); // finished for party B move to next phase
	[receiveB] phase=3 & party=1 & n=N-1 & i=L -> (phase'=4); // finished protocol (reveal values)
	
	// FINISHED
	[] phase=4 -> (phase'=4); // loop
	
endmodule

// party A
module partyA
	
	// bi the number of bits of B's ith secret A knows 
	// (keep pairs of secrets together to give a more structured model)
	b : array[40] of [0..L]; // should be 2*N not 40 now
	
	// first step (get either secret i or (N-1)+i with equal probability)
	[receiveA] phase=1 & n<=19 -> 0.5 : (b[n]'=L)  + 0.5 : (b[N+n]'=L);
	// second step (secrets 0,...,N-1)
	[receiveA] phase=2 & n<=19 -> (b[n]'=min(b[n]+1,L));
	// second step (secrets N,...,2N-1)
	[receiveA] phase=3 & n<=19  -> (b[N+n]'=min(b[N+n]+1,L));

endmodule

// construct module for party B through renaming
module partyB=partyA[b=a, receiveA=receiveB] endmodule

// formulae
formula kB = ( (a[0]=L  & a[20]=L)
			 | (a[1]=L  & a[21]=L)
			 | (a[2]=L  & a[22]=L)
			 | (a[3]=L  & a[23]=L)
			 | (a[4]=L  & a[24]=L)
			 | (a[5]=L  & a[25]=L)
			 | (a[6]=L  & a[26]=L)
			 | (a[7]=L  & a[27]=L)
			 | (a[8]=L  & a[28]=L)
			 | (a[9]=L  & a[29]=L)
			 | (a[10]=L & a[30]=L)
			 | (a[11]=L & a[31]=L)
			 | (a[12]=L & a[32]=L)
			 | (a[13]=L & a[33]=L)
			 | (a[14]=L & a[34]=L)
			 | (a[15]=L & a[35]=L)
			 | (a[16]=L & a[36]=L)
			 | (a[17]=L & a[37]=L)
			 | (a[18]=L & a[38]=L)
			 | (a[19]=L & a[39]=L));

formula kA = ( (b[0]=L  & b[20]=L)
			 | (b[1]=L  & b[21]=L)
			 | (b[2]=L  & b[22]=L)
			 | (b[3]=L  & b[23]=L)
			 | (b[4]=L  & b[24]=L)
			 | (b[5]=L  & b[25]=L)
			 | (b[6]=L  & b[26]=L)
			 | (b[7]=L  & b[27]=L)
			 | (b[8]=L  & b[28]=L)
			 | (b[9]=L  & b[29]=L)
			 | (b[10]=L & b[30]=L)
			 | (b[11]=L & b[31]=L)
			 | (b[12]=L & b[32]=L)
			 | (b[13]=L & b[33]=L)
			 | (b[14]=L & b[34]=L)
			 | (b[15]=L & b[35]=L)
			 | (b[16]=L & b[36]=L)
			 | (b[17]=L & b[37]=L)
			 | (b[18]=L & b[38]=L)
			 | (b[19]=L & b[39]=L));

// labels
label "knowB" = kB;
label "knowA" = kA;

// reward structures

// messages from B that A needs to knows a pair once B knows a pair
rewards "messages_A_needs"
	[receiveA] kB & !kA : 1;
endrewards

// messages from A that B needs to knows a pair once A knows a pair
rewards "messages_B_needs"
	[receiveA] kA & !kB : 1;
endrewards
