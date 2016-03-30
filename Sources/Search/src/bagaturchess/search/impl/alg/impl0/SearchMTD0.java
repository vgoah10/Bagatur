/*
 *  BagaturChess (UCI chess engine and tools)
 *  Copyright (C) 2005 Krasimir I. Topchiyski (k_topchiyski@yahoo.com)
 *  
 *  Open Source project location: http://sourceforge.net/projects/bagaturchess/develop
 *  SVN repository https://bagaturchess.svn.sourceforge.net/svnroot/bagaturchess
 *
 *  This file is part of BagaturChess program.
 * 
 *  BagaturChess is open software: you can redistribute it and/or modify
 *  it under the terms of the Eclipse Public License version 1.0 as published by
 *  the Eclipse Foundation.
 *
 *  BagaturChess is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Eclipse Public License for more details.
 *
 *  You should have received a copy of the Eclipse Public License version 1.0
 *  along with BagaturChess. If not, see <http://www.eclipse.org/legal/epl-v10.html/>.
 *
 */
package bagaturchess.search.impl.alg.impl0;


import bagaturchess.bitboard.api.IBitBoard;
import bagaturchess.bitboard.impl.Constants;
import bagaturchess.bitboard.impl.Fields;
import bagaturchess.bitboard.impl.Figures;
import bagaturchess.bitboard.impl.movegen.MoveInt;
import bagaturchess.bitboard.impl.utils.VarStatistic;
import bagaturchess.egtb.gaviota.GTBProbeOutput;
import bagaturchess.search.api.internal.IRootWindow;
import bagaturchess.search.api.internal.ISearch;
import bagaturchess.search.api.internal.ISearchInfo;
import bagaturchess.search.api.internal.ISearchMediator;
import bagaturchess.search.api.internal.ISearchMoveList;
import bagaturchess.search.api.internal.ISearchMoveListFactory;

import bagaturchess.search.impl.alg.SearchImpl;
import bagaturchess.search.impl.alg.SearchImpl_MTD;
import bagaturchess.search.impl.alg.impl5_scratch.SearchMoveListFactory5;
import bagaturchess.search.impl.env.SearchEnv;
import bagaturchess.search.impl.exts.ExtStat;
import bagaturchess.search.impl.pv.PVNode;
import bagaturchess.search.impl.tpt.TPTEntry;
import bagaturchess.search.impl.utils.SearchUtils;


public class SearchMTD0 extends SearchImpl_MTD {
	
	//IBitBoard bitboard  = new Board("8/8/p7/P7/4P3/1kp5/4K3/8 w - - 1 82"); //Unstoppable passers problem?
	
	public static final ExtStat extStat = new ExtStat();
	
	//public MoveEvalStat evals = new MoveEvalStat();
	
	int MIN_EVAL_DIFF_PV = 33;
	int MIN_EVAL_DIFF_NONPV = 33;
	
	
	public SearchMTD0(Object[] args) {
		this(new SearchEnv((IBitBoard) args[0], getOrCreateSearchEnv(args)));
	}
	
	public SearchMTD0(SearchEnv _env) {
		super(_env);
	}
	
	
	protected ISearchMoveListFactory getMoveListFactory() {
		return new SearchMoveListFactory5();
	}
	
	
	protected boolean allowIllegalMoves() {
		return false;
	}
	
	
	@Override
	public String toString() {
		String result = "";//"" + this + " ";
		
		result += Thread.currentThread().getName() + "	>	";
		result += getEnv().toString();
		
		return result;
	}
	
	private boolean isPasserPushNonPV(int cur_move) {
		//return false;
		boolean passerPush = env.getBitboard().isPasserPush(cur_move);
		return passerPush;
	}
	
	private boolean isPasserPushPV(int cur_move) {
		//return false;
		boolean passerPush = env.getBitboard().isPasserPush(cur_move);
		return passerPush;
	}
	
	private boolean isDangerousPV(int cur_move, int initial_maxdepth, int depth) {
		
		int totalMaterialFactor = env.getBitboard().getMaterialFactor().getTotalFactor();
		
		//if (true) return false; 
		
		/**
		 * King moves in endgame - 4rooks or 2queens 
		 */
		int type = MoveInt.getFigureType(cur_move);
		if (type == Figures.TYPE_KING
				&& totalMaterialFactor <= 30 //2queens + 4minors
				&& totalMaterialFactor > 16 //2rooks + 2minors
				) {
			if (depth <= initial_maxdepth) {
				return true;
			}
		}
		
		/**
		 * Queen tropism increased
		 */
		if (type == Figures.TYPE_QUEEN) {
			int fromID = MoveInt.getFromFieldID(cur_move);
			int toID = MoveInt.getToFieldID(cur_move);
			
			int fromTropism = -1;
			int toTropism = -1;
			
			int colour = env.getBitboard().getColourToMove();
			if (colour == Figures.COLOUR_WHITE) {
				int opKingID = env.getBitboard().getPiecesLists().getPieces(Constants.PID_B_KING).getData()[0];
				fromTropism = Fields.getDistancePoints_reversed(opKingID, fromID);
				toTropism = Fields.getDistancePoints_reversed(opKingID, toID);
			} else {
				int opKingID = env.getBitboard().getPiecesLists().getPieces(Constants.PID_W_KING).getData()[0];
				fromTropism = Fields.getDistancePoints_reversed(opKingID, fromID);
				toTropism = Fields.getDistancePoints_reversed(opKingID, toID);
			}
			
			return toTropism - fromTropism >= 1;// && toTropism >= 3;
			
		} else if (type == Figures.TYPE_KNIGHT) {
			/**
			 * Knight tropism increas
			 */
			int fromID = MoveInt.getFromFieldID(cur_move);
			int toID = MoveInt.getToFieldID(cur_move);
			
			int fromTropism = -1;
			int toTropism = -1;
			
			int colour = env.getBitboard().getColourToMove();
			if (colour == Figures.COLOUR_WHITE) {
				if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_W_QUEEN).getDataSize() > 0) {
					int opKingID = env.getBitboard().getPiecesLists().getPieces(Constants.PID_B_KING).getData()[0];
					fromTropism = Fields.getDistancePoints_reversed(opKingID, fromID);
					toTropism = Fields.getDistancePoints_reversed(opKingID, toID);
				}
			} else {
				if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_B_QUEEN).getDataSize() > 0) {
					int opKingID = env.getBitboard().getPiecesLists().getPieces(Constants.PID_W_KING).getData()[0];
					fromTropism = Fields.getDistancePoints_reversed(opKingID, fromID);
					toTropism = Fields.getDistancePoints_reversed(opKingID, toID);
				}
			}
			
			return toTropism - fromTropism >= 1;//2 && toTropism >= 3;
		}
		
		return false;
	}
	
	private boolean isDangerousNonPV(int cur_move, int initial_maxdepth, int depth) {
		
		
		int totalMaterialFactor = env.getBitboard().getMaterialFactor().getTotalFactor();
		
		/**
		 * King moves in endgame - 4rooks or 2queens 
		 */
		int type = MoveInt.getFigureType(cur_move);
		if (type == Figures.TYPE_KING
				&& totalMaterialFactor <= 30 //2queens + 4minors
				&& totalMaterialFactor > 16 //2rooks + 2minors
				) {
			if (depth <= initial_maxdepth) {
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isKillerMove(int cur_move) {
		
		return false;
		
		/*boolean isKiller = false;
		int[] killers = env.getHistory_all().getNonCaptureKillers(env.getBitboard().getColourToMove());
		for (int i=0; i<killers.length; i++) {
			if (cur_move == killers[i]) {
				isKiller = true;
				break;
			}
		}
		return isKiller;*/
	}
	
	private int interpolateByMaterialAndColour(int colour, int val_o, int val_e) {
		return env.getBitboard().getMaterialFactor().interpolateByFactorAndColour(colour, val_o, val_e);
	}
	
	/*private int interpolateByMaterialAndColour(int val_o, int val_e) {
		return env.getBitboard().getBaseEvaluation().interpolateByFactorAndColour(env.getBitboard().getColourToMove(), val_o, val_e);
	}
	
	private int interpolateByOpponentMaterialAndColour(int val_o, int val_e) {
		int opColour = Figures.OPPONENT_COLOUR[env.getBitboard().getColourToMove()];
		return env.getBitboard().getBaseEvaluation().interpolateByFactorAndColour(opColour, val_o, val_e);
	}
	
	private int interpolateByTotalMaterial(int val_o, int val_e) {
		return env.getBitboard().getBaseEvaluation().interpolateByFactor(val_o, val_e);
	}*/
	
	
	protected int new_maxdepth_pv(int colourToMove, int maxdepth, int move, boolean inCheck, boolean singleMove, int see, boolean passerPush, int move_eval,
			int oldMaterialGain, int newMaterialGain, boolean mateThreat) {
		int extend = 0;
		
		if (inCheck) {
			if (EXT_INCHECK_PV < ISearch.PLY) {
				throw new IllegalStateException();
			}
			extend += EXT_INCHECK_PV;
			extStat.pv_InCheck++;
		} else {
			
			if (MoveInt.getFigureType(move) == Figures.TYPE_KING) { 
				//extend = env.getBitboard().getMaterialFactor().interpolateByFactor(0, PLY - 4);
			}
			
			if (MoveInt.isPromotion(move)) {
				extend = Math.max(extend, EXT_PROMOTION_PV);
				extStat.pv_Prom++;
			} else if (passerPush) {
				extend = Math.max(extend, getEnv().getExtensions().getPasserPushPV(colourToMove).calc(EXT_PASSER_PUSH_PV));//ext_PasserPush.calc(interpolateByMaterialAndColour(0, EXT_PASSER_PUSH_PV));
				extStat.pv_PasserPush++;
			}
			
			if (mateThreat) {
				//extend += interpolateByOpponentMaterialAndColour(EXT_MATE_THREAT_PV, 0);
				extend = Math.max(extend, getEnv().getExtensions().getMateThreadPV(colourToMove).calc(EXT_MATE_THREAT_PV));
				extStat.nonpv_Mate++;
			}
			
			if (see > 0 && MoveInt.isCapture(move)) {
				if (MoveInt.getCapturedFigureType(move) == Figures.TYPE_PAWN) {
					extend = Math.max(extend, getEnv().getExtensions().getWinCapPawnPV(colourToMove).calc(EXT_WINCAP_PAWN_PV));
					extStat.pv_CapPawn++;
				} else {
					extend = Math.max(extend, getEnv().getExtensions().getWinCapNonPawnPV(colourToMove).calc(EXT_WINCAP_NONPAWN_PV));
					extStat.pv_CapNonPawn++;
				}
			}
			
			if (oldMaterialGain < 0 && newMaterialGain >= 0) {
				extend = Math.max(extend, getEnv().getExtensions().getRecapturePV(colourToMove).calc(EXT_RECAPTURE_PV));
			}
			
			if (move_eval > MIN_EVAL_DIFF_PV) {
				if (!MoveInt.isCaptureOrPromotion(move) && !passerPush && !mateThreat && !singleMove) {
					extend = Math.max(extend, getEnv().getExtensions().getMoveEval(colourToMove).calc(EXT_MOVE_EVAL_PV));
				}
			}
		}
		
		if (singleMove) {
			extStat.pv_SingleMove++;
		}
		
		return maxdepth + Math.min(PLY, extend) + (singleMove ? getEnv().getExtensions().getSingleReplyPV(colourToMove).calc(EXT_SINGLE_REPLY_PV) : 0);
		//return maxdepth + Math.min(2 * PLY, extend + (singleMove ? getEnv().getExtensions().getSingleReplyPV().calc(EXT_SINGLE_REPLY_PV) : 0));
	}
	
	protected int new_maxdepth_nullwin(int colourToMove, int maxdepth, int move, boolean inCheck, boolean singleMove, int see,
			boolean mateThreat, boolean passerPush, int move_eval, int oldMaterialGain, int newMaterialGain) {
		
		int extend = 0;
		
		if (inCheck) {
			if (EXT_INCHECK_PV < ISearch.PLY) {
				throw new IllegalStateException();
			}
			extend = Math.max(extend, EXT_INCHECK_NONPV);
			extStat.nonpv_InCheck++;
		} else {
			
			if (MoveInt.getFigureType(move) == Figures.TYPE_KING) { 
				//extend = env.getBitboard().getMaterialFactor().interpolateByFactor(0, PLY - 4);
			}
			
			if (MoveInt.isPromotion(move)) {
				extend = Math.max(extend, EXT_PROMOTION_NONPV);
				extStat.nonpv_Prom++;
			} else if (passerPush) {
				extend = Math.max(extend, getEnv().getExtensions().getPasserPushNonPV(colourToMove).calc(EXT_PASSER_PUSH_NONPV));//ext_PasserPush.calc(interpolateByMaterialAndColour(0, EXT_PASSER_PUSH_NONPV));
				extStat.nonpv_PasserPush++;
			}
			
			if (mateThreat) {
				//extend += interpolateByOpponentMaterialAndColour(EXT_MATE_THREAT_NONPV, 0);
				extend = Math.max(extend, getEnv().getExtensions().getMateThreadNonPV(colourToMove).calc(EXT_MATE_THREAT_NONPV));
				extStat.nonpv_Mate++;
			}
			
			if (see > 0 && MoveInt.isCapture(move)) {
				if (MoveInt.getCapturedFigureType(move) == Figures.TYPE_PAWN) {
					extend = Math.max(extend, getEnv().getExtensions().getWinCapPawnNonPV(colourToMove).calc(EXT_WINCAP_PAWN_NONPV));
					extStat.nonpv_CapPawn++;
				} else {
					extend = Math.max(extend, getEnv().getExtensions().getWinCapNonPawnNonPV(colourToMove).calc(EXT_WINCAP_NONPAWN_NONPV));
					extStat.nonpv_CapNonPawn++;
				}
			}
			
			if (oldMaterialGain < 0 && newMaterialGain >= 0) {
				extend = Math.max(extend, getEnv().getExtensions().getRecaptureNonPV(colourToMove).calc(EXT_RECAPTURE_NONPV));
			}
			
			if (move_eval > MIN_EVAL_DIFF_NONPV) {
				if (!MoveInt.isCaptureOrPromotion(move) && !passerPush && !mateThreat && !singleMove) {
					extend = Math.max(extend, getEnv().getExtensions().getMoveEval(colourToMove).calc(EXT_MOVE_EVAL_NONPV));
				}
			}
		}
		
		if (singleMove) {
			extStat.nonpv_SingleMove++;
		}
		
		return maxdepth + Math.min(PLY, extend) + (singleMove ? getEnv().getExtensions().getSingleReplyNonPV(colourToMove).calc(EXT_SINGLE_REPLY_NONPV) : 0);
		//return maxdepth + Math.min(2 * PLY, extend + (singleMove ? getEnv().getExtensions().getSingleReplyNonPV().calc(EXT_SINGLE_REPLY_NONPV) : 0));
	}
	
	private int interpolateDangerByMyKingSuspense(int ext) {
		if (env.getBitboard().getColourToMove() == Figures.COLOUR_WHITE) {
			if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_B_QUEEN).getDataSize() >= 1) {
				//ext = ext;
			} else {
				ext = interpolateByMaterialAndColour(Figures.COLOUR_BLACK, ext, 0);
			}
		} else {
			if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_W_QUEEN).getDataSize() >= 1) {
				//ext = ext;
			} else {
				ext = interpolateByMaterialAndColour(Figures.COLOUR_WHITE, ext, 0);
			}
		}
		return ext;
	}
	
	/*private int getSingleReplyExtPV1(int maxdepth) {
		if (env.getBitboard().getColourToMove() == Figures.COLOUR_WHITE) {
			if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_B_QUEEN).getDataSize() >= 1) {
				maxdepth += EXT_SINGLE_REPLY_PV;
			} else {
				int ext = interpolateByMaterialAndColour(Figures.COLOUR_BLACK, EXT_SINGLE_REPLY_PV, 0);
				maxdepth += ext;
			}
		} else {
			if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_W_QUEEN).getDataSize() >= 1) {
				maxdepth += EXT_SINGLE_REPLY_PV;
			} else {
				int ext = interpolateByMaterialAndColour(Figures.COLOUR_WHITE, EXT_SINGLE_REPLY_PV, 0);
				maxdepth += ext;
			}
		}
		return maxdepth;
	}
	
	private int getSingleReplyExtNonPV1(int maxdepth) {
		if (env.getBitboard().getColourToMove() == Figures.COLOUR_WHITE) {
			if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_B_QUEEN).getDataSize() >= 1) {
				maxdepth += EXT_SINGLE_REPLY_NONPV;
			} else {
				int ext = interpolateByMaterialAndColour(Figures.COLOUR_BLACK, EXT_SINGLE_REPLY_NONPV, 0);
				maxdepth += ext;
			}
		} else {
			if (env.getBitboard().getPiecesLists().getPieces(Constants.PID_W_QUEEN).getDataSize() >= 1) {
				maxdepth += EXT_SINGLE_REPLY_NONPV;
			} else {
				int ext = interpolateByMaterialAndColour(Figures.COLOUR_WHITE, EXT_SINGLE_REPLY_NONPV, 0);
				maxdepth += ext;
			}
		}
		return maxdepth;
	}*/
	
	public void newSearch() {
		
		super.newSearch();
		
	}
	
	
	private int getLMR1(ISearchMoveList list) {
		return (int) Math.max(1, Math.sqrt(list.size()) / (double)2);
	}
	
	private int getLMR2(ISearchMoveList list) {
		return (int) Math.max(1, Math.sqrt(list.size()));
	}
	
	
	public int pv_search(ISearchMediator mediator, IRootWindow rootWin, ISearchInfo info, int initial_maxdepth,
			int maxdepth, int depth, int alpha_org, int beta, int prevbest, int prevprevbest,
			int[] prevPV, boolean prevNullMove, int evalGain, int rootColour, int totalLMReduction,
			int materialGain, boolean inNullMove, int mateMove, boolean useMateDistancePrunning) {
		
		//System.out.println("TPT   > " + env.getTPT().getUsage());
		//System.out.println("PAWNS > " + env.getPawnsCache().getUsage());
		//System.out.println("EVAL  > " + env.getEvalCache().getUsage());
		//System.out.println("");
		
		//SHITY
		/*if (depth == 0 && initial_maxdepth <= 2 * PLY) {
			evals = new MoveEvalStat();
		}*/
		
		if (depth == 0) {
			//System.out.println("SR: " + ext_SingleReply.calc(PLY));
			//System.out.println("MT: " + ext_MateThread.calc(PLY) + "	" + ext_MateThread.getRate());
		}
		
		if (alpha_org >= beta) {
			throw new IllegalStateException("alpha=" + alpha_org + ", beta=" + beta);
		}
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		long hashkey = env.getBitboard().getHashKey();
		
		if (depth >= MAX_DEPTH) {
			return fullEval(depth, alpha_org, beta, rootColour);
		}
		
		if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, alpha_org, beta);
		
		PVNode node = pvman.load(depth);
		
		node.bestmove = 0;
		node.eval = MIN;
		node.nullmove = false;
		node.leaf = true;
		
		if (isDrawPV(depth)) {
			node.eval = getDrawScores(rootColour);
			return node.eval;
		}
		
		

		
		
		boolean inCheck = env.getBitboard().isInCheck();
		
	    // Mate distance pruning
		if (USE_MATE_DISTANCE && !inCheck && useMateDistancePrunning && depth >= 1) {
			
		      /*if (inCheck && !env.getBitboard().hasMove()) {
					node.bestmove = 0;
					node.eval = -getMateVal(depth);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
		      }*/
		      
		      // lower bound
		      int value = -getMateVal(depth+2); // does not work if the current position is mate
		      if (value > alpha_org) {
		    	  alpha_org = value;
		         if (value >= beta) {
						node.bestmove = 0;
						node.eval = value;
						node.leaf = true;
						node.nullmove = false;
						return node.eval;
		         }
		      }
		      
		      // upper bound
		      
		      value = getMateVal(depth+1);
		      
		      if (value < beta) {
		         beta = value;
		         if (value <= alpha_org) {
						node.bestmove = 0;
						node.eval = value;
						node.leaf = true;
						node.nullmove = false;
						return node.eval;
		         }
		      }
		}
	    
		boolean singleMove = false;
		//if (inCheck) {
			//singleMove = env.getBitboard().hasSingleMove();
		//}
		
		
		int rest = normDepth(maxdepth) - depth;
		
		//boolean mateThreat = false;
		int new_mateMove = 0;
		if (USE_MATE_EXT_PV && !inNullMove && rest <= 1) {
			
			if (rest < 0) {
				throw new IllegalStateException();
			}
			
			if (!inCheck) { //It will be extended with check ext
				env.getBitboard().makeNullMoveForward();
				int null_val = -pv_search(mediator, rootWin, info, initial_maxdepth, PLY * (depth + 1), depth,
						-beta, -alpha_org, prevprevbest, prevbest, prevPV, true, -evalGain, rootColour,
						totalLMReduction, -materialGain, true, 0, useMateDistancePrunning);
				
				if (null_val < 0 && isMateVal(null_val)
						//&& normDepth(maxdepth) <= 2 * normDepth(initial_maxdepth)
						) {
					
					//mateThreat = true;
					
					TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
					if (entry != null) {
						new_mateMove = entry.getBestMove_lower();
						if (new_mateMove == 0) {
							new_mateMove = entry.getBestMove_upper();
						}
					}
					
					if (rest == 1) {
						maxdepth = PLY * (depth + 2);
					} else if (rest < 1) {
						maxdepth = PLY * (depth + 2);
					}
				}
				
				env.getBitboard().makeNullMoveBackward();
				
				node.bestmove = 0;
				node.eval = MIN;
				node.nullmove = false;
				node.leaf = true;
			}
		}
		
		
		rest = normDepth(maxdepth) - depth;
		
		boolean disableExts = false;
		if (inCheck && rest < 1) {
			disableExts = true;
			
			maxdepth += EXT_INCHECK_PV;
			
			/*if (EXT_SINGLE_REPLY_PV > 0) {
				if (singleMove && rest <= 1) {
					maxdepth = getSingleReplyExtPV(maxdepth, colourToMove);
				}
			}*/
			
			if (depth >= normDepth(maxdepth)) {
				maxdepth = PLY * (depth + 1);
			}
		}
		
		
		if (depth >= normDepth(maxdepth)) {
			
			if (inCheck) {
				throw new IllegalStateException();
			}
			
			int staticEval = fullEval(depth, alpha_org, beta, rootColour);
			//if (staticEval >= beta || staticEval + env.getEval().getMaterialQueen() + 100 < alpha_org) {
			//	node.eval = staticEval;
			//} else {
				node.eval = pv_qsearch(mediator, info, initial_maxdepth, depth, alpha_org, beta, 0, staticEval, true, rootColour);	
			//}
			
			//if (node.eval > alpha_org && env.getTactics().silentButDeadly()) {
			//	maxdepth = PLY * (depth + 1);
			//} else {
				return node.eval;
			//}
		}
		
		//int tpt_move = getTPTMove(hashkey);
		/*int tpt_depth = 0;
		int tpt_move = 0;
		
		env.getTPT().lock();
		{
			TPTEntry tptEntry = env.getTPT().get(hashkey);
			if (tptEntry != null) {
				tpt_depth = tptEntry.getDepth();
				tpt_move = tptEntry.getBestMove_lower();
			}
		}
		env.getTPT().unlock();*/
		
		boolean tpt_found = false;
		boolean tpt_exact = false;
		int tpt_depth = 0;
		int tpt_lower = MIN;
		int tpt_upper = MAX;
		int tpt_move = 0;
		
		if (allowTPTAccess(maxdepth, depth)) {
			env.getTPT().lock();
			{
				TPTEntry tptEntry = env.getTPT().get(hashkey);
				if (tptEntry != null) {
					tpt_found = true;
					tpt_exact = tptEntry.isExact();
					tpt_depth = tptEntry.getDepth();
					tpt_lower = tptEntry.getLowerBound();
					tpt_upper = tptEntry.getUpperBound();
					tpt_move = tptEntry.getBestMove_lower();
				}
			}
			env.getTPT().unlock();
		}
		
		if (getSearchConfig().isOther_UseTPTScoresPV() 
				&& tpt_found && tpt_depth >= rest
			) {
			if (tpt_exact) {
				if (!SearchUtils.isMateVal(tpt_lower)) {
					node.bestmove = tpt_move;
					node.eval = tpt_lower;
					node.leaf = true;
					node.nullmove = false;
					
					env.getTPT().lock();
					buff_tpt_depthtracking[0] = 0;
					extractFromTPT(info, rest, node, true, buff_tpt_depthtracking);
					env.getTPT().unlock();
					
					if (buff_tpt_depthtracking[0] >= rest) {
						return node.eval;
					}
				}
			} else {
				if (tpt_lower >= beta) {
					if (!SearchUtils.isMateVal(tpt_lower)) {
						node.bestmove = tpt_move;
						node.eval = tpt_lower;
						node.leaf = true;
						node.nullmove = false;
						
						
						env.getTPT().lock();
						buff_tpt_depthtracking[0] = 0;
						extractFromTPT(info, rest, node, true, buff_tpt_depthtracking);
						env.getTPT().unlock();
						
						if (buff_tpt_depthtracking[0] >= rest) {
							return node.eval;
						}
					}
				}
				if (tpt_upper <= beta - 1) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						node.bestmove = tpt_move;
						node.eval = tpt_upper;
						node.leaf = true;
						node.nullmove = false;
						
						
						env.getTPT().lock();
						buff_tpt_depthtracking[0] = 0;
						extractFromTPT(info, rest, node, false, buff_tpt_depthtracking);
						env.getTPT().unlock();
						
						if (buff_tpt_depthtracking[0] >= rest) {
							return node.eval;
						}
					}
				}
			}
		}
		
		
		rest = normDepth(maxdepth) - depth;
		
		int staticEval = roughEval(depth, rootColour);
		//int staticEval = fullEval(depth, alpha_org, beta, rootColour);
		
		//boolean hasAtLeastOnePiece = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getBaseEvaluation().getWhiteFactor() >= 3 :
		//																		env.getBitboard().getBaseEvaluation().getBlackFactor() >= 3;
		
		
		int egtb_val = Integer.MIN_VALUE;
		if (env.getGTBProbing() != null
				&& useMateDistancePrunning
				&& depth >= 3
				&& rest >= 2
				&& env.getBitboard().getColourToMove() == rootColour
			) {
			
			env.getGTBProbing().probe(env.getBitboard(), gtb_probe_result);
			
			if (gtb_probe_result[0] == GTBProbeOutput.DRAW) {
				egtb_val = getDrawScores(rootColour);
			} else {
				int result = extractEGTBMateValue(depth);
				if (result != 0) {
					egtb_val = result;
				}
			}
			
			if (egtb_val != Integer.MIN_VALUE) {
				
				/*node.bestmove = 0;
				node.eval = egtb_val;
				node.leaf = true;
				node.nullmove = false;
				return egtb_val;
				*/
				
				/*if (egtb_val > 0 && isMateVal(egtb_val)) {
					node.bestmove = 0;
					node.eval = egtb_val;
					node.leaf = true;
					node.nullmove = false;
					return egtb_val;
				}*/
				
				if (egtb_val >= -200) {
					node.bestmove = 0;
					node.eval = egtb_val;
					node.leaf = true;
					node.nullmove = false;
					return egtb_val;
				} else {
					if (!isMateVal(egtb_val)) {
						throw new IllegalStateException("egtb_val=" + egtb_val);
					}
				}
			}
		}
		
		
		boolean mateThreat = false;
		
		if (NULL_MOVE
				&& false
				&& !inCheck
				&& !prevNullMove
				&& staticEval >= beta + 35
				&& (allowTPTAccess(maxdepth, depth))
				) {
			
			//int null_maxdepth = maxdepth - PLY * (rest >= 6 ? 3 : 2);
			//int null_maxdepth = maxdepth - PLY * (rest >= 6 ? 3 : 2);
			int null_reduction = PLY * (rest >= 6 ? 4 : 3);
			//int null_reduction = PLY * Math.max(3, rest / 2);
			null_reduction = Math.max(null_reduction, PLY * (rest / 2));
			
			int null_maxdepth = maxdepth - null_reduction;
			
			
			env.getBitboard().makeNullMoveForward();
			//int null_val = -pv_search(mediator, stopper, rootWin, info, initial_maxdepth, maxdepth - PLY * (rest >= 6 ? 3 : 2), depth,
					//-(beta-1), -beta, prevprevbest, prevbest, prevPV, true, -evalGain, rootColour, totalLMReduction, -materialGain, true, 0);
			int null_val = -nullwin_search(mediator, info,
					initial_maxdepth, null_maxdepth,
					depth + 1, -(beta - 1), true, prevprevbest, prevbest, prevPV, rootColour, totalLMReduction, -materialGain, true, 0, useMateDistancePrunning);
			
			if (staticEval > alpha_org) { //PV node candidate
				if (null_val <= alpha_org) { //but bad thing appears
					//if (null_val < 0 && isMateVal(null_val)) {//and the bad thing is mate
						mateThreat = true;
						
						
							env.getTPT().lock();
							TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
							if (entry != null) {
								new_mateMove = entry.getBestMove_lower();
								if (new_mateMove == 0) {
									new_mateMove = entry.getBestMove_upper();
								}
							}
							env.getTPT().unlock();
					//}
				}
			}
			env.getBitboard().makeNullMoveBackward();
		}

		
		//IID PV Node
		//int iid_eval = MIN;
		if (IID_PV && depth > 0) {
			
			int reduction = Math.max(2, rest / 2);
			int iidRest = normDepth(maxdepth - PLY * reduction) - depth;
			
			if (tpt_depth < iidRest
				&& normDepth(maxdepth) - reduction > depth
				) {
				
				nullwin_search(mediator, info, initial_maxdepth, maxdepth - PLY * reduction, depth, beta,
						prevNullMove, prevbest, prevprevbest, prevPV, rootColour, totalLMReduction, materialGain, true, mateMove, useMateDistancePrunning);
				//pv_search(mediator, stopper, info, initial_maxdepth, maxdepth - PLY * reduction, depth, alpha_org, beta,
				//		 prevbest, prevprevbest, prevPV, prevNullMove);
				
				if (allowTPTAccess(maxdepth, depth)) {
					env.getTPT().lock();
					TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
					if (entry != null) {
						tpt_lower = entry.getLowerBound();
						tpt_move = entry.getBestMove_lower();
						if (tpt_move == 0) {
							tpt_move = entry.getBestMove_upper();
						}
					}
					env.getTPT().unlock();
				}
			}
		}
		
		
		if (!inCheck
			&& !isMateVal(alpha_org)
			&& !isMateVal(beta)
			&& rest <= normDepth(maxdepth) / 2
			
			) {
			
			if (tpt_lower > MIN) {
				if (alpha_org > tpt_lower + optimisticPositionEval(mediator, rest) ) {
					
					node.eval = tpt_lower;
					node.leaf = true;
					node.nullmove = false;
					
					env.getTPT().lock();
					buff_tpt_depthtracking[0] = 0;
					extractFromTPT(info, rest, node, true, buff_tpt_depthtracking);
					env.getTPT().unlock();
					
					return node.eval;
				}
			} /*else {
				
				if (rest <= normDepth(maxdepth) / 4){
					if (alpha_org > staticEval + optimisticPositionEval(mediator, rest)) {
						staticEval = fullEval(depth, alpha_org, beta, rootColour);
		                int qeval = pv_qsearch(mediator, info, initial_maxdepth, depth, alpha_org, beta, 0, staticEval, true, rootColour);
						if (alpha_org > qeval + optimisticPositionEval(mediator, rest) ) {
							//node.bestmove = 0;
							node.eval = qeval;
							//node.leaf = true;
							//node.nullmove = false;
							return node.eval;
						}
					}
				}
			}*/
		}
		
		
		node.bestmove = 0;
		node.eval = MIN;
		node.nullmove = false;
		node.leaf = true;
		
		ISearchMoveList list = null;
		
		if (!inCheck) {
			list = lists_all[depth];
			list.clear();
			list.setTptMove(tpt_move);
			list.setPrevBestMove(prevprevbest);
			list.setMateMove(mateMove);
			
			
			if (prevPV != null && depth < prevPV.length) {
				list.setPrevpvMove(prevPV[depth]);
			}
		} else {
			list = lists_escapes[depth];
			list.clear();
			list.setTptMove(tpt_move);
			list.setPrevBestMove(prevprevbest);
		}
		
		
		boolean statisticAdded = false;
		
		//boolean pvNode = false;
		int searchedCount = 0;
		int legalMoves = 0;
		int alpha = alpha_org;
		int best_eval = MIN;
		int best_move = 0;
		
		//boolean pvSet = false;
		
		/*if (!env.getBitboard().isPossible(mateMove)) {
			mateMove = 0;
		}*/
		
		int cur_move = (tpt_move != 0) ? tpt_move : list.next();
		if (cur_move != 0) {
			do {
				
				if (searchedCount > 0 && cur_move == tpt_move) {
					continue;
				}
				
				if (mediator != null) {
					if (depth == 0) {
						info.setCurrentMove(cur_move);
						info.setCurrentMoveNumber((searchedCount + 1));
					}
					if ((initial_maxdepth / PLY) <= 7) {
						if (depth == 0) { 
							mediator.changedMinor(info);
						}
					} else {
						double send_depth = (initial_maxdepth / PLY) / (double) 2;
						if (depth <= send_depth) { 
							mediator.changedMinor(info);
						}
					}
				}
				
				boolean passerPush = isPasserPushPV(cur_move);
				boolean isCapOrProm = MoveInt.isCaptureOrPromotion(cur_move);
				int moveSee = -1;
				if (isCapOrProm) {
					moveSee = env.getBitboard().getSee().evalExchange(cur_move);
				}
				int new_materialGain = materialGain + env.getBitboard().getMaterialFactor().getMaterialGain(cur_move);
				//boolean isDangerous = isDangerousPV(cur_move, normDepth(initial_maxdepth), depth);
				
				//boolean isKiller = isKillerMove(cur_move);
				
				int eval_b = EXT_MOVE_EVAL_PV > 0 ? fullEval(depth, alpha, beta, rootColour) : 0;
				
				/*if (!env.getBitboard().isPossible(cur_move)) {
					throw new IllegalStateException("cur_move=" + cur_move + ", tpt_move=" + tpt_move);
				}*/
				
				env.getBitboard().makeMoveForward(cur_move);
				
				if (env.getBitboard().isInCheck(colourToMove)) {
					if (allowIllegalMoves()) {
						env.getBitboard().makeMoveBackward(cur_move);
						continue;
					} else {
						throw new IllegalStateException();	
					}
				}
				legalMoves++;
				
				int eval_a = EXT_MOVE_EVAL_PV > 0 ? -fullEval(depth, -beta, -alpha, rootColour) : 0;
				
				boolean isCheckMove = env.getBitboard().isInCheck();
				
				int move_eval = eval_a - eval_b;
				int new_evalgain = evalGain + move_eval;
				
				//if (!isCapOrProm) evals.addMoveEval(move_eval, colourToMove, isCapOrProm, true);				
				
				int new_maxdepth = maxdepth;
				if (depth > 0 && !disableExts) {
					new_maxdepth = new_maxdepth_pv(colourToMove, maxdepth, cur_move, inCheck, singleMove, moveSee, passerPush, move_eval, materialGain, new_materialGain, mateThreat);
				}
				
				//int barrier_1 = isCapOrProm ? 0 : evals.getTop2Eval(colourToMove, isCapOrProm, true);
				//int barrier_2 = 0;//isCapOrProm ? 0 : evals.getTop2Eval(colourToMove, isCapOrProm, true);
				
				
				if (!inCheck
						&& !isMateVal(alpha_org)
						&& !isMateVal(beta)
						&& !isCheckMove
						//&& (!isCapOrProm || REDUCE_CAPTURES)
						//&& maxdepth == new_maxdepth
						//&& searchedCount >= getLMR1(list)
						&& searchedCount >= STATIC_PRUNING_PV_INDEX
						&& rest < STATIC_REDUCTION_MARGIN_PV.length
						) {
						
					int optimisticEval = staticEval + env.getBitboard().getMaterialFactor().getMaterialGain(cur_move);
						//if (alpha_org >= optimisticEval + STATIC_REDUCTION_MARGIN_PV[rest]
						if (alpha_org > optimisticEval + 1 * Math.max(1, (rest / (double) 2 )) * optimisticPositionEval(mediator, rest)
						) {
							info.setSearchedNodes(info.getSearchedNodes() + 1);
							searchedCount++;
							env.getBitboard().makeMoveBackward(cur_move);
							
							continue;
						}
				}
				
				
				int cur_eval;
				if (searchedCount == 0 || (USE_PV_IN_ALL_ROOTS && depth == 0)) {

					cur_eval = -pv_search(mediator, rootWin, info, initial_maxdepth, new_maxdepth, depth + 1, -beta, -alpha,
							best_move, prevbest, prevPV, false, -new_evalgain, rootColour,
							totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
				} else {
					
					if (searchedCount >= getLMR1(list) //LMR_PV_INDEX1
							 && !inCheck
							 && !isCheckMove
							 //&& (!isCapOrProm || REDUCE_CAPTURES)
							 && moveSee < 0
							 //&& !passerPush
							 && maxdepth == new_maxdepth
							 //&& (!env.getHistory_all().isGoodMove(cur_move) || REDUCE_HISTORY_MOVES)
							 //&& !mateThreat
							 //&& !isDangerous
							 //&& totalLMReduction < maxdepth / 2
							) {
						
						int lmrReduction = 0;
						if (searchedCount >= getLMR1(list)) {
							lmrReduction += PLY;
							if (rest > 2 && searchedCount >= getLMR2(list)) {
								lmrReduction += PLY;
								if (rest > 3 && searchedCount >= 2 * getLMR2(list)) {
									lmrReduction += PLY;
									if (rest > 4 && searchedCount >= 4 * getLMR2(list)) {
										lmrReduction += PLY;
										if (rest > 5 && searchedCount >= 8 * getLMR2(list)) {
											lmrReduction += PLY;
										}
									}
								}
							}
						}
						
						double rate = env.getHistory_all().getGoodMoveScores(cur_move);
						lmrReduction *= (double)(1 - rate);
						if (lmrReduction < PLY) {
							lmrReduction = PLY;
						}
						
						/*int lmrReduction = (searchedCount >= getLMR2(list) && rest > 2) ? 2 : 1;
						lmrReduction *= PLY;
						
						if (!REDUCE_HISTORY_MOVES) {
							lmrReduction *= (1 - env.getHistory_all().getGoodMoveScores(cur_move));
							if (lmrReduction < PLY) {
								lmrReduction = PLY;
							}
						}*/
						
						cur_eval = -nullwin_search(mediator, info, initial_maxdepth,
								maxdepth - lmrReduction, depth + 1, -alpha, false,
								best_move, prevbest, prevPV, rootColour, totalLMReduction + lmrReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
						
						if (cur_eval >= beta && lmrReduction > 0) {
							cur_eval = -nullwin_search(mediator, info, initial_maxdepth, maxdepth, depth + 1, -alpha, false,
									best_move, prevbest, prevPV, rootColour, totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
						}
					} else {
						cur_eval = -nullwin_search(mediator, info, initial_maxdepth, maxdepth, depth + 1, -alpha, false,
								best_move, prevbest, prevPV, rootColour, totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
					}
					
					/*if (mateThreat
							&& cur_eval > alpha
							&& maxdepth + PLY > new_maxdepth) {
						new_maxdepth = maxdepth + PLY;
						cur_eval = -nullwin_search(mediator, stopper, info, new_maxdepth, depth + 1, -alpha, false, best_move, prevbest, prevPV);
					}*/
					
					if (isPVNode(cur_eval, best_eval, alpha, beta)) {
						
						/*int	shallow_eval = -1;
						if (new_maxdepth > maxdepth) {
							shallow_eval = -pv_search(mediator, rootWin, info, initial_maxdepth, maxdepth, depth + 1, -beta, -alpha,
									best_move, prevbest, prevPV, false, -new_evalgain, rootColour,
									totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
						}*/
						
						cur_eval = -pv_search(mediator, rootWin, info, initial_maxdepth, new_maxdepth, depth + 1, -beta, -alpha,
								best_move, prevbest, prevPV, false, -new_evalgain, rootColour,
								totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
						
						/*if (new_maxdepth > maxdepth) {
							int score_diff = shallow_eval - cur_eval;
							int ext_depth = new_maxdepth - maxdepth;
							if (EXT_SINGLE_REPLY_PV > 0 && singleMove) {
								getEnv().getExtensions().getSingleReplyPV(colourToMove).inc(score_diff, ext_depth);
							}
							if (EXT_MATE_THREAT_PV > 0 && mateThreat) {
								getEnv().getExtensions().getMateThreadPV(colourToMove).inc(score_diff, ext_depth);
							}
							if (EXT_PASSER_PUSH_PV > 0 && passerPush) {
								getEnv().getExtensions().getPasserPushPV(colourToMove).inc(score_diff, ext_depth);
							}
							if (EXT_RECAPTURE_PV > 0 && materialGain < 0 && new_materialGain >= 0) {
								getEnv().getExtensions().getRecapturePV(colourToMove).inc(score_diff, ext_depth);
							}
							if (moveSee > 0 && MoveInt.isCapture(cur_move)) {
								if (EXT_WINCAP_PAWN_PV > 0 && MoveInt.getCapturedFigureType(cur_move) == Figures.TYPE_PAWN) {
									getEnv().getExtensions().getWinCapPawnPV(colourToMove).inc(score_diff, ext_depth);
								} else if (EXT_WINCAP_NONPAWN_PV > 0) {
									getEnv().getExtensions().getWinCapNonPawnPV(colourToMove).inc(score_diff, ext_depth);
								}
							}
							
							if (EXT_MOVE_EVAL_PV > 0 && move_eval > MIN_EVAL_DIFF_PV) {
								if (!MoveInt.isCaptureOrPromotion(cur_move) && !passerPush && !mateThreat && !singleMove) {
									getEnv().getExtensions().getMoveEval(colourToMove).inc(score_diff, ext_depth);
								}
							}
						}*/
					}
				}
				
				env.getBitboard().makeMoveBackward(cur_move);
				
				if (cur_eval > best_eval) {
					
					best_eval = cur_eval;
					best_move = cur_move;
					
					//boolean inside = rootWin.isInside(cur_eval, colourToMove);
					//if ((inside && isNonAlphaNode(cur_eval, best_eval, alpha, beta))
						//	|| (!inside && !pvSet)) {
					if (isNonAlphaNode(cur_eval, best_eval, alpha, beta)) {
					//if (inside && isNonAlphaNode(cur_eval, best_eval, alpha, beta)) {
						
						//pvSet = true;
						
						node.bestmove = best_move;
						node.eval = best_eval;
						node.leaf = false;
						node.nullmove = false;
						
						if (depth + 1 < MAX_DEPTH) {
							
							/*env.getBitboard().makeMoveForward(cur_move);
							int[] pv = PVNode.convertPV(PVNode.extractPV(pvman.load(depth + 1)));
							for (int i=0; i<pv.length; i++) {
								int move = pv[i];
								try {
									env.getBitboard().makeMoveForward(move);
								} catch(java.lang.IllegalStateException ie) {
									String msg = "ERROR " + hashkey + "	" + i + "	" + MoveInt.movesToString(pv) + "	MOVE: " + MoveInt.moveToString(move);
									System.out.println(msg);
									ie.printStackTrace();
									System.exit(0);
								}
							}
							for (int i=pv.length - 1; i>=0; i--) {
								int move = pv[i];
								env.getBitboard().makeMoveBackward(move);
							}
							env.getBitboard().makeMoveBackward(cur_move);
							*/
							
							pvman.store(depth + 1, node, pvman.load(depth + 1), true);
						}
					}
					
					if (best_eval > alpha) {
						alpha = best_eval; 
						//pvNode = true;
					}
					
					if (best_eval >= beta) {
						
						if (tpt_move == best_move) {
							list.countStatistics(best_move);
						}
						list.updateStatistics(best_move);
						
						statisticAdded = true;
						
						if (inCheck) {
							env.getHistory_check().goodMove(cur_move, rest * rest, best_eval > 0 && isMateVal(best_eval));
						} else {
							env.getHistory_all().goodMove(cur_move, rest * rest, best_eval > 0 && isMateVal(best_eval));
							env.getHistory_all().counterMove(env.getBitboard().getLastMove(), cur_move);
						}
						
						break;
					}
				}
				
				searchedCount++;
			} while ((cur_move = list.next()) != 0);
		}
		
		if (!statisticAdded) {
			if (tpt_move == best_move) {
				list.countStatistics(best_move);
			}
			list.updateStatistics(best_move);
		}
		
		if (best_move != 0 && (best_eval == MIN || best_eval == MAX)) {
			throw new IllegalStateException();
		}
		
		if (best_move == 0) {
			if (inCheck) {
				if (legalMoves == 0) {
					node.bestmove = 0;
					node.eval = -getMateVal(depth);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				} else {
					throw new IllegalStateException("hashkey=" + hashkey);
				}
			} else {
				if (legalMoves == 0) {
					node.bestmove = 0;
					node.eval = getDrawScores(rootColour);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				} else {
					//throw new IllegalStateException("hashkey=" + hashkey);
					node.bestmove = 0;
					node.eval = staticEval;
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				}
			}
		}
		
		if (best_move == 0 || best_eval == MIN || best_eval == MAX) {
			throw new IllegalStateException();
		}
		
		
		if (allowTPTAccess(maxdepth, depth)) {
			env.getTPT().lock();
			env.getTPT().put(hashkey, normDepth(maxdepth), depth, colourToMove, best_eval, alpha_org, beta, best_move, (byte)0);
			env.getTPT().unlock();
		}
		
		return best_eval;
	}
	
	//int allow_true = 0;
	//int allow_false = 0;
	private boolean allowTPTAccess(int maxdepth, int depth) {
		
		if (getSearchConfig().getTPTUsageDepthCut() == 0) {
			return true;
		}

		return depth + getSearchConfig().getTPTUsageDepthCut() <= normDepth(maxdepth);
		
		//boolean result = depth < normDepth(maxdepth) / 2 || depth < normDepth(maxdepth) - 1;
		//return tpt_access.access() || depth < normDepth(maxdepth) / 2;
	}
	

	public int nullwin_search(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth,
			int maxdepth, int depth, int beta,
			boolean prevNullMove, int prevbest, int prevprevbest, int[] prevPV, int rootColour, int totalLMReduction, int materialGain, boolean inNullMove, int mateMove,
			boolean useMateDistancePrunning) {
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		long hashkey = env.getBitboard().getHashKey();
		
		
		if (depth >= MAX_DEPTH) {
			return lazyEval(depth, beta - 1, beta, rootColour);
		}
		
		if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, beta - 1, beta);
				
		
		if (isDraw()) {
			return getDrawScores(rootColour);
		}
		
		
		boolean inCheck = env.getBitboard().isInCheck();
		
		int alpha_org = beta - 1;
		
	    // Mate distance pruning
		if (USE_MATE_DISTANCE && !inCheck && useMateDistancePrunning && depth >= 1) {
		      
			// lower bound
		      /*if (inCheck && !env.getBitboard().hasMove()) {
					return -getMateVal(depth);
		      }*/
		      
		      int value = -getMateVal(depth+2); // does not work if the current position is mate

		      if (value > alpha_org) {
		         if (value >= beta) {
					return value;
		         }
		      }

		      // upper bound

		      value = getMateVal(depth+1);

		      if (value < beta) {
		         beta = value;
		         if (value <= alpha_org) {
						return value;
		         }
		      }
		}
	    
		
		boolean singleMove = false;
		//if (inCheck) {
			//singleMove = env.getBitboard().hasSingleMove();
		//}
		
		int rest = normDepth(maxdepth) - depth;
		
		boolean mateThreat = false;
		int new_mateMove = 0;
		if (USE_MATE_EXT_NONPV && !inNullMove && rest <= 1 && rest >= 0) {
			
			if (rest < 0) {
				throw new IllegalStateException();
			}
			
			if (!inCheck) { //It will be extended with check ext
				env.getBitboard().makeNullMoveForward();
				int null_val = -nullwin_search(mediator, info, initial_maxdepth, PLY * (depth + 2),
						depth + 1, -(beta - 1), true, prevprevbest, prevbest, prevPV, rootColour, totalLMReduction, -materialGain, true, 0, useMateDistancePrunning);
				
				if (null_val < 0 && isMateVal(null_val)) {
					
					//mateThreat = true;
					
					TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
					if (entry != null) {
						new_mateMove = entry.getBestMove_lower();
						if (new_mateMove == 0) {
							new_mateMove = entry.getBestMove_upper();
						}
					}
					
					if (rest == 1) {
						maxdepth = PLY * (depth + 2);
					} else {
						maxdepth = PLY * (depth + 2);
					}
				}
				
				env.getBitboard().makeNullMoveBackward();
			}
		}
		
		boolean disableExts = false;
		if (inCheck && rest < 1) {
			disableExts = true;
			
			maxdepth += EXT_INCHECK_PV;
			
			rest = normDepth(maxdepth) - depth;
			
			/*if (EXT_SINGLE_REPLY_NONPV > 0) {
				if (singleMove && rest <= 1) {
					maxdepth = getSingleReplyExtPV(maxdepth, colourToMove);
				}
			}*/
			
			if (depth >= normDepth(maxdepth)) {
				maxdepth = PLY * (depth + 1);
			}
		}
		
		if (depth >= normDepth(maxdepth)) {
			
			if (inCheck) {
				throw new IllegalStateException();
			}
			
			int staticEval = lazyEval(depth, beta -1, beta, rootColour);
			//if (staticEval >= beta || staticEval + env.getEval().getMaterialQueen() + 100 < beta - 1) {
				//return staticEval;
			//} else {
				int eval = nullwin_qsearch(mediator, info, initial_maxdepth, depth, beta, 0, staticEval, true, rootColour);
			//}
			
			//if (eval > alpha_org && env.getTactics().silentButDeadly()) {
			//	maxdepth = PLY * (depth + 1);
			//} else {
				return eval;
			//}
		}

		rest = normDepth(maxdepth) - depth;
		
		boolean tpt_found = false;
		boolean tpt_exact = false;
		int tpt_depth = 0;
		int tpt_lower = MIN;
		int tpt_upper = MAX;
		int tpt_move = 0;
		
		if (allowTPTAccess(maxdepth, depth)) {
			env.getTPT().lock();
			{
				TPTEntry tptEntry = env.getTPT().get(hashkey);
				if (tptEntry != null) {
					tpt_found = true;
					tpt_exact = tptEntry.isExact();
					tpt_depth = tptEntry.getDepth();
					tpt_lower = tptEntry.getLowerBound();
					tpt_upper = tptEntry.getUpperBound();
					tpt_move = tptEntry.getBestMove_lower();
				}
			}
			env.getTPT().unlock();
		}
		
		if (USE_TPT_SCORES && tpt_found && tpt_depth >= rest) {
			if (tpt_exact) {
				if (!SearchUtils.isMateVal(tpt_lower)) {
					return tpt_lower;
				}
			} else {
				if (tpt_lower >= beta) {
					if (!SearchUtils.isMateVal(tpt_lower)) {
						return tpt_lower;
					}
				}
				if (tpt_upper <= beta - 1) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						return tpt_upper;
					}
				}
			}
		}
		
		int staticEval = roughEval(depth, rootColour);
		//int staticEval = lazyEval(depth, alpha_org, beta, rootColour);
		
		boolean hasAtLeastOnePiece = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getMaterialFactor().getWhiteFactor() >= 3 :
																				env.getBitboard().getMaterialFactor().getBlackFactor() >= 3;
		
		boolean hasAtLeastThreePieces = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getMaterialFactor().getWhiteFactor() >= 9 :
																				env.getBitboard().getMaterialFactor().getBlackFactor() >= 9;
		
		int egtb_val = Integer.MIN_VALUE;
		if (env.getGTBProbing() != null
				&& useMateDistancePrunning
				&& depth >= 3
				&& rest >= 2
				&& env.getBitboard().getColourToMove() == rootColour
			) {
			
			env.getGTBProbing().probe(env.getBitboard(), gtb_probe_result);
			
			if (gtb_probe_result[0] == GTBProbeOutput.DRAW) {
				egtb_val = getDrawScores(rootColour);
			} else {
				int result = extractEGTBMateValue(depth);
				if (result != 0) {
					egtb_val = result;
				}
			}
			
			if (egtb_val != Integer.MIN_VALUE) {
				
				//return egtb_val;
				
				/*if (egtb_val > 0 && isMateVal(egtb_val)) {
					return egtb_val;
				}*/
				
				if (egtb_val >= -200) {
					return egtb_val;
				} else {
					if (!isMateVal(egtb_val)) {
						throw new IllegalStateException("egtb_val=" + egtb_val);
					}
				}
			}
		}
																				
																				
		int interval = 500;
		int sign = +1;
		double null_move_factor = 35;//sign * 2 * (env.getBitboard().getMaterialFactor().interpolateByFactor(0, interval) - (interval/2));
		//System.out.println(fact);
		
		
		boolean zungzwang = false;
		mateThreat = false;
		if (NULL_MOVE
				&& !inCheck
				&& !prevNullMove
				&& hasAtLeastOnePiece
				&& rest >= 1
				&& staticEval >= beta + null_move_factor
				) {
			
			//if (true) throw new IllegalStateException("implement null move");
			
			//int null_reduction = PLY * (rest >= 6 ? 3 : 2);
			int null_reduction = PLY * (rest >= 6 ? 4 : 3);
			null_reduction = Math.max(null_reduction, PLY * (rest / 2));
			
			int null_maxdepth = maxdepth - null_reduction;
			
			env.getBitboard().makeNullMoveForward();
			int null_val = -nullwin_search(mediator, info,
					initial_maxdepth, null_maxdepth,
					depth + 1, -(beta - 1), true, prevprevbest, prevbest, prevPV, rootColour, totalLMReduction, -materialGain, true, 0, useMateDistancePrunning);
			
			/*if (null_val < 0 && isMateVal(null_val)) {
				
				TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
				if (entry != null) {
					new_mateMove = entry.getBestMove_lower();
					if (new_mateMove == 0) {
						new_mateMove = entry.getBestMove_upper();
					}
				}
				
				if (rest == 1) {
					maxdepth = PLY * (depth + 2);
				} else {
					maxdepth = PLY * (depth + 2);
				}
			}*/
			
			if (null_val >= beta) {
				
				env.getBitboard().makeNullMoveBackward();
				
				if (hasAtLeastThreePieces) {
					return null_val;
				}
				
				int null_val_ver = nullwin_search(mediator, info, initial_maxdepth, null_maxdepth, depth,
						beta, prevNullMove, prevbest, prevprevbest, prevPV, rootColour, totalLMReduction, materialGain, true, mateMove, useMateDistancePrunning);
				
				if (null_val_ver >= beta) {
					return null_val_ver;
				} else {
					zungzwang = true;
				}
				
				if (allowTPTAccess(maxdepth, depth)) {
					env.getTPT().lock();
					{
						TPTEntry tptEntry = env.getTPT().get(hashkey);
						if (tptEntry != null) {
							tpt_found = true;
							tpt_exact = tptEntry.isExact();
							tpt_depth = tptEntry.getDepth();
							tpt_lower = tptEntry.getLowerBound();
							tpt_upper = tptEntry.getUpperBound();
							tpt_move = tptEntry.getBestMove_lower();
						}
					}
					env.getTPT().unlock();
				}
			} else {
				if (staticEval > alpha_org) { //PV node candidate
					if (null_val <= alpha_org) { //but bad thing appears
						//if (null_val < 0 && isMateVal(null_val)) {//and the bad thing is mate
							mateThreat = true;
							
							if (allowTPTAccess(maxdepth, depth)) {
								env.getTPT().lock();
								TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
								if (entry != null) {
									new_mateMove = entry.getBestMove_lower();
									if (new_mateMove == 0) {
										new_mateMove = entry.getBestMove_upper();
									}
								}
								env.getTPT().unlock();
							}
						//}
					}
				}
				
				env.getBitboard().makeNullMoveBackward();
			}
			
		}
		//Razoring:
	    /*else if(RAZORING && !inCheck) {
	    	if ((rest < 4 && staticEval < beta - 300) ||
		            (rest < 2 && staticEval < beta - 100)) {
	    		throw new IllegalStateException();
		      //int qeval = nullwin_qsearch(stopper, info, initial_maxdepth, depth, beta, 0, );
		      //if (qeval < beta) return qeval;
	    	}
	    }*/
		
		
        //Razoring:
		if(RAZORING && !inCheck) {
        
			/*if (
                    (rest <= 1 && staticEval < beta - 100) ||
                    (rest <= 2 && staticEval < beta - 300) ||
                    (rest <= 3 && staticEval < beta - 500)
                              ) {
                              
                          int qeval = nullwin_qsearch(mediator, info, initial_maxdepth, depth, beta, 0, staticEval, true, rootColour);
                                         if (qeval < beta) {
                                                        return qeval;
                                         }
            }*/
        
        
	        if (rest < STATIC_REDUCTION_MARGIN_NONPV.length) {
	              
                  if (staticEval < beta - STATIC_REDUCTION_MARGIN_NONPV[rest]) {
                     int qeval = nullwin_qsearch(mediator, info, initial_maxdepth, depth, beta, 0, staticEval, true, rootColour);
                                    if (qeval < beta) {
                                                   return qeval;
                                    }
                  }
	        }
        
		}

		
		//IID NONPV Node
		if (IID_NONPV) {
			
			int reduction = Math.max(2, rest / 2);
			int iidRest = normDepth(maxdepth - PLY * reduction) - depth;
			
			if (tpt_depth < iidRest
					&& normDepth(maxdepth) - reduction > depth) {
				
				nullwin_search(mediator, info, initial_maxdepth, maxdepth - PLY * reduction, depth, beta,
						prevNullMove, prevbest, prevprevbest, prevPV, rootColour, totalLMReduction, materialGain, inNullMove, mateMove, useMateDistancePrunning);
				
				if (allowTPTAccess(maxdepth, depth)) {
					env.getTPT().lock();
					TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
					if (entry != null) {
						tpt_lower = entry.getLowerBound();
						tpt_move = entry.getBestMove_lower();
						if (tpt_move == 0) {
							tpt_move = entry.getBestMove_upper();
						}
					}
					env.getTPT().unlock();
				}
				
			}
		}
		
		
		if (!inCheck
			&& !isMateVal(alpha_org)
			&& !isMateVal(beta)
			&& rest <= normDepth(maxdepth) / 2
			
			) {
			
			if (tpt_lower > MIN) {
				if (alpha_org > tpt_lower + optimisticPositionEval(mediator, rest) ) {
					return tpt_lower;
				}
			} /*else {
				if (rest <= normDepth(maxdepth) / 4){
					if (alpha_org > staticEval + optimisticPositionEval(mediator, rest)) {
						staticEval = lazyEval(depth, beta - 1, beta, rootColour);
						int qeval = nullwin_qsearch(mediator, info, initial_maxdepth, depth, beta, 0, staticEval, true, rootColour);
						if (alpha_org > qeval + optimisticPositionEval(mediator, rest) ) {
							return qeval;
						}
					}
				}
			}*/
		}
		
		
		ISearchMoveList list = null;
		
		if (!inCheck) {
			list = lists_all[depth];
			list.clear();
			list.setTptMove(tpt_move);
			list.setPrevBestMove(prevprevbest);
			list.setMateMove(mateMove);
			
			if (prevPV != null && depth < prevPV.length) {
				list.setPrevpvMove(prevPV[depth]);
			}
		} else {
			list = lists_escapes[depth];
			list.clear();
			list.setTptMove(tpt_move);
			list.setPrevBestMove(prevprevbest);
		}
		
		
		boolean statisticAdded = false;
		
		int searchedCount = 0;
		int legalMoves = 0;
		int best_eval = MIN;
		int best_move = 0;
		
		int cur_move = (tpt_move != 0) ? tpt_move : list.next();
		if (cur_move != 0) {
			do {
				
				if (searchedCount > 0 && cur_move == tpt_move) {
					continue;
				}
				
				boolean isCapOrProm = MoveInt.isCaptureOrPromotion(cur_move);
				int moveSee = -1;
				if (isCapOrProm) {
					moveSee = env.getBitboard().getSee().evalExchange(cur_move);
				}
				int new_materialGain = materialGain + env.getBitboard().getMaterialFactor().getMaterialGain(cur_move);
				boolean passerPush = isPasserPushNonPV(cur_move);
				
				//boolean isDangerous = isDangerousNonPV(cur_move, normDepth(initial_maxdepth), depth);
				//boolean isKiller = isKillerMove(cur_move);
				
				int eval_b = EXT_MOVE_EVAL_NONPV > 0 ? fullEval(depth, beta - 1, beta, rootColour) : 0;
				
				env.getBitboard().makeMoveForward(cur_move);
				
				if (env.getBitboard().isInCheck(colourToMove)) {
					if (allowIllegalMoves()) {
						env.getBitboard().makeMoveBackward(cur_move);
						continue;
					} else {
						throw new IllegalStateException();	
					}
				}
				legalMoves++;
				
				//int eval_a = EXT_MOVE_EVAL_NONPV > 0 ? -fullEval(depth, beta - 1, beta, rootColour) : 0;
				int eval_a = EXT_MOVE_EVAL_NONPV > 0 ? -fullEval(depth, -beta, -(beta - 1), rootColour) : 0;
				
				boolean isCheckMove = env.getBitboard().isInCheck();
				
				int move_eval = eval_a - eval_b;
				
				//if (!isCapOrProm) evals.addMoveEval(cur_evalgain, colourToMove, isCapOrProm, false);		
				
				int new_maxdepth = maxdepth;
				if (depth > 0 && !disableExts) {
					new_maxdepth = new_maxdepth_nullwin(colourToMove, maxdepth, cur_move, inCheck, singleMove, moveSee, mateThreat, passerPush, move_eval, materialGain, new_materialGain);
				}
				
				
				if (!inCheck
						&& !isMateVal(alpha_org)
						&& !isMateVal(beta)
						&& !isCheckMove
						//&& (!isCapOrProm || REDUCE_CAPTURES)
						//&& maxdepth == new_maxdepth
						//&& searchedCount >= getLMR1(list)
						&& searchedCount >= STATIC_PRUNING_NONPV_INDEX
						&& rest < STATIC_REDUCTION_MARGIN_NONPV.length
						) {
						
					int optimisticEval = staticEval + env.getBitboard().getMaterialFactor().getMaterialGain(cur_move);
						//if (alpha_org >= optimisticEval + STATIC_REDUCTION_MARGIN_NONPV[rest]				
						if (alpha_org > optimisticEval + 1 * Math.max(1, (rest / (double) 2 )) * optimisticPositionEval(mediator, rest)
						) {
							info.setSearchedNodes(info.getSearchedNodes() + 1);
							searchedCount++;
							env.getBitboard().makeMoveBackward(cur_move);
							
							continue;
						}
				}
				
				//int top1 = isCapOrProm ? 0 : evals.getTop1Eval(colourToMove, isCapOrProm, false);
				//int top2 = isCapOrProm ? 0 : evals.getTop2Eval(colourToMove, isCapOrProm, false);
				
				int cur_eval = 0;
				if (searchedCount >= getLMR1(list) //LMR_NONPV_INDEX1
						 && !inCheck
						 && !isCheckMove
						 //&& (!isCapOrProm || REDUCE_CAPTURES)
						 && moveSee < 0
						 //&& !passerPush
						 && maxdepth == new_maxdepth
						 && (REDUCE_HISTORY_MOVES || env.getHistory_all().getGoodMoveScores(cur_move) < 0.5)
						 //&& (!env.getHistory_all().isGoodMove(cur_move) || REDUCE_HISTORY_MOVES)
						 //&& !isDangerous
						 //&& totalLMReduction < maxdepth / 2
						) {
					
					int lmrReduction = 0;
					if (searchedCount >= getLMR1(list)) {
						lmrReduction += PLY;
						if (rest > 2 && searchedCount >= getLMR2(list)) {
							lmrReduction += PLY;
							if (rest > 3 && searchedCount >= 2 * getLMR2(list)) {
								lmrReduction += PLY;
								if (rest > 4 && searchedCount >= 4 * getLMR2(list)) {
									lmrReduction += PLY;
									if (rest > 5 && searchedCount >= 8 * getLMR2(list)) {
										lmrReduction += PLY;
									}
								}
							}
						}
					}
					
					double rate = env.getHistory_all().getGoodMoveScores(cur_move);
					lmrReduction *= (double)(1 - rate);
					if (lmrReduction < PLY) {
						lmrReduction = PLY;
					}
					
					/*int lmrReduction = (searchedCount >= getLMR2(list) && rest > 2) ? 2 : 1;
					lmrReduction *= PLY;
					
					if (!REDUCE_HISTORY_MOVES) {
						lmrReduction *= (1 - env.getHistory_all().getGoodMoveScores(cur_move));
						if (lmrReduction < PLY) {
							lmrReduction = PLY;
						}
					}*/
					
					cur_eval = -nullwin_search(mediator, info, initial_maxdepth,
							new_maxdepth - lmrReduction, depth + 1, -(beta - 1), false,
							best_move, prevbest, prevPV, rootColour, totalLMReduction + lmrReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
					
					if (cur_eval >= beta && lmrReduction > 0) {
						cur_eval = -nullwin_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -(beta - 1), false,
								best_move, prevbest, prevPV, rootColour, totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
					}
					
				} else {
					
					int	shallow_eval = -1;
					if (new_maxdepth > maxdepth) {
						shallow_eval = -nullwin_search(mediator, info, initial_maxdepth, maxdepth, depth + 1, -(beta - 1), false,
								best_move, prevbest, prevPV, rootColour, totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
					}
					
					cur_eval = -nullwin_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -(beta - 1), false,
							best_move, prevbest, prevPV, rootColour, totalLMReduction, -new_materialGain, inNullMove, new_mateMove, useMateDistancePrunning);
					
					if (new_maxdepth > maxdepth) {
						int score_diff = shallow_eval - cur_eval;
						int ext_depth = new_maxdepth - maxdepth;
						if (EXT_SINGLE_REPLY_NONPV > 0 && singleMove) {
							getEnv().getExtensions().getSingleReplyNonPV(colourToMove).inc(score_diff, ext_depth);
						}
						if (EXT_MATE_THREAT_NONPV > 0 && mateThreat) {
							getEnv().getExtensions().getMateThreadNonPV(colourToMove).inc(score_diff, ext_depth);
						}
						if (EXT_PASSER_PUSH_NONPV > 0 && passerPush) {
							getEnv().getExtensions().getPasserPushNonPV(colourToMove).inc(score_diff, ext_depth);
						}
						if (EXT_RECAPTURE_NONPV > 0 && materialGain < 0 && new_materialGain >= 0) {
							getEnv().getExtensions().getRecaptureNonPV(colourToMove).inc(score_diff, ext_depth);
						}
						if (moveSee > 0 && MoveInt.isCapture(cur_move)) {
							if (EXT_WINCAP_PAWN_NONPV > 0 && MoveInt.getCapturedFigureType(cur_move) == Figures.TYPE_PAWN) {
								getEnv().getExtensions().getWinCapPawnNonPV(colourToMove).inc(score_diff, ext_depth);
							} else if (EXT_WINCAP_NONPAWN_NONPV > 0) {
								getEnv().getExtensions().getWinCapNonPawnNonPV(colourToMove).inc(score_diff, ext_depth);
							}
						}
						
						if (EXT_MOVE_EVAL_NONPV > 0 && move_eval > MIN_EVAL_DIFF_NONPV) {
							if (!MoveInt.isCaptureOrPromotion(cur_move) && !passerPush && !mateThreat && !singleMove) {
								getEnv().getExtensions().getMoveEval(colourToMove).inc(score_diff, ext_depth);
							}
						}
					}
				}
				
				env.getBitboard().makeMoveBackward(cur_move);
				
				if (cur_eval > best_eval) {
					
					best_eval = cur_eval;
					best_move = cur_move;
					
					if (best_eval >= beta) {
						
						if (tpt_move == best_move) {
							list.countStatistics(best_move);
						}
						list.updateStatistics(best_move);
						
						statisticAdded = true;
						
						if (inCheck) {
							env.getHistory_check().goodMove(cur_move, rest * rest, best_eval > 0 && isMateVal(best_eval));
						} else {
							env.getHistory_all().goodMove(cur_move, rest * rest, best_eval > 0 && isMateVal(best_eval));
							env.getHistory_all().counterMove(env.getBitboard().getLastMove(), cur_move);
						}
						
						break;
					}
					
					if (best_eval > beta - 1) {
						throw new IllegalStateException(); 
					}
				}
				
				searchedCount++;
			} while ((cur_move = list.next()) != 0);
		}
		
		if (!statisticAdded) {
			if (tpt_move == best_move) {
				list.countStatistics(best_move);
			}
			list.updateStatistics(best_move);
		}
		
		if (best_move != 0 && (best_eval == MIN || best_eval == MAX)) {
			throw new IllegalStateException();
		}
		
		if (best_move == 0) {
			if (inCheck) {
				if (legalMoves == 0) {
					return -getMateVal(depth);
				} else {
					throw new IllegalStateException("hashkey=" + hashkey);
					//return best_eval;
				}
			} else {
				if (legalMoves == 0) {
					return getDrawScores(rootColour);
				} else {
					//throw new IllegalStateException("hashkey=" + hashkey);
					return staticEval;
				}
			}
		}
		
		
		if (best_move == 0 || best_eval == MIN || best_eval == MAX) {
			throw new IllegalStateException();
		}
		
		
		if (allowTPTAccess(maxdepth, depth)) {
			env.getTPT().lock();
			env.getTPT().put(hashkey, normDepth(maxdepth), depth, colourToMove, best_eval, beta - 1, beta, best_move, (byte)0);
			env.getTPT().unlock();
		}
		
		return best_eval;

	}
	
	private double optimisticPositionEval(ISearchMediator mediator, int rest) {
		
		//int DEPTH1_INTERVAL = 100;
		//int DEPTH1_INTERVAL = (int) (move_eval_diff.getDisperse());
		//int DEPTH1_INTERVAL = (int) (move_eval_diff.getEntropy());
		//int DEPTH1_INTERVAL = (int) ((move_eval_diff.getEntropy() + move_eval_diff.getDisperse()) / 2);
		
		//return 32;//Math.max(33,  DEPTH1_INTERVAL * (rest / (double) 2));
		//return Math.max(1,  DEPTH1_INTERVAL * (rest / (double) 2));
		
		return mediator.getTrustWindow_AlphaAspiration();
	}
	
	private int pv_qsearch(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth, int depth, int alpha_org, int beta, int matgain, int initialStaticEval, boolean firstTime, int rootColour) {
		
		if (!firstTime) info.setSearchedNodes(info.getSearchedNodes() + 1);	
		
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		long hashkey = env.getBitboard().getHashKey();
		
		if (hashkey == -3770350413322162370L) {
			int g = 0;
		}

		int staticEval = firstTime ? initialStaticEval : fullEval(depth, alpha_org, beta, rootColour);
		if (depth >= MAX_DEPTH) {
			return staticEval;
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		
		if (!firstTime) if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, alpha_org, beta);
		
		PVNode node = pvman.load(depth);
		node.bestmove = 0;
		node.eval = MIN;
		node.nullmove = false;
		node.leaf = true;
		
		if (isDrawPV(depth)) {
			node.eval = getDrawScores(rootColour);
			return node.eval;
		}
		
		boolean inCheck = env.getBitboard().isInCheck();
		
	    // Mate distance pruning
		if (USE_MATE_DISTANCE && !inCheck && depth >= 1) {

		      /*if (inCheck && !env.getBitboard().hasMove()) {
					node.bestmove = 0;
					node.eval = -getMateVal(depth);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
		      }*/
		      
		      // lower bound
		      int value = -getMateVal(depth+2); // does not work if the current position is mate

		      if (value > alpha_org) {
		    	  alpha_org = value;
		         if (value >= beta) {
						node.bestmove = 0;
						node.eval = value;
						node.leaf = true;
						node.nullmove = false;
						return node.eval;
		         }
		      }

		      // upper bound

		      value = getMateVal(depth+1);

		      if (value < beta) {
		         beta = value;
		         if (value <= alpha_org) {
						node.bestmove = 0;
						node.eval = value;
						node.leaf = true;
						node.nullmove = false;
						return node.eval;
		         }
		      }
		}
		
		if (!inCheck) {
			if (staticEval >= beta) {
				node.eval = staticEval;
				return node.eval;
			}
			
			if (!isMateVal(alpha_org)
					&& staticEval + env.getEval().getMaterialQueen() + 100 < alpha_org) {
				node.eval = staticEval;
				return node.eval;
			}
			
			if (staticEval > alpha_org) {
				alpha_org = staticEval;
			}
		}

		//int tpt_move = getTPTMove(hashkey);
		boolean tpt_exact = false;
		boolean tpt_found = false;
		int tpt_move = 0;
		int tpt_lower = MIN;
		int tpt_upper = MAX;
		
		if (allowTPTAccess(initial_maxdepth, depth)) {
			env.getTPT().lock();
			{
				TPTEntry tptEntry = env.getTPT().get(hashkey);
				if (tptEntry != null) {
					tpt_found = true;
					tpt_move = tptEntry.getBestMove_lower();
					
					tpt_exact = tptEntry.isExact();
					tpt_lower = tptEntry.getLowerBound();
					tpt_upper = tptEntry.getUpperBound();
				}
			}
			env.getTPT().unlock();
		}
		
		if (USE_TPT_SCORES_PV_QSEARCH && tpt_found) {
			if (tpt_exact) {
				if (!SearchUtils.isMateVal(tpt_lower)) {
					node.bestmove = tpt_move;
					node.eval = tpt_lower;
					node.leaf = true;
					node.nullmove = false;
					return tpt_lower;
				}
			} else {
				if (tpt_lower >= beta) {
					if (!SearchUtils.isMateVal(tpt_lower)) {
						node.bestmove = tpt_move;
						node.eval = tpt_lower;
						node.leaf = true;
						node.nullmove = false;
						return tpt_lower;
					}
				}
				if (tpt_upper <= alpha_org) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						node.bestmove = tpt_move;
						node.eval = tpt_upper;
						node.leaf = true;
						node.nullmove = false;
						return tpt_upper;
					}
				}
			}
		}
		
		
		ISearchMoveList list = null;
		if (inCheck) { 
			list = lists_escapes[depth];
			list.clear();
			list.setTptMove(tpt_move);
		} else {
			list = lists_capsproms[depth];
			list.clear();
			list.setTptMove(tpt_move);
		}
		
		int legalMoves = 0;
		int best_eval = MIN;
		int best_move = 0;
		int cur_move = 0;
		
		int alpha = alpha_org;
		
		if (inCheck) {
			cur_move = (tpt_move != 0) ? tpt_move : list.next();
		} else {
			cur_move = (tpt_move != 0 && MoveInt.isCaptureOrPromotion(tpt_move)) ? tpt_move : list.next();
			//cur_move = (tpt_move != 0) ? tpt_move : list.next();
		}

		if (env.getBitboard().getHashKey() == 9168619578754754311L) {
			int g = 0;
		}
		
		int searchedMoves = 0;
		if (cur_move != 0) 
		do {
			
			if (searchedMoves > 0 && cur_move == tpt_move) {
				continue;
			}
			searchedMoves++;
			
			if (MoveInt.isCapture(cur_move)) {
				if (MoveInt.getCapturedFigureType(cur_move) == Figures.TYPE_KING) {
					throw new IllegalStateException(env.getBitboard().toString());
				}
			}
			
			
			int new_matgain = matgain + env.getBitboard().getMaterialFactor().getMaterialGain(cur_move);
			
			int moveSee = USE_SEE_IN_QSEARCH ? env.getBitboard().getSee().evalExchange(cur_move) : 0;
			
			if (inCheck
					|| (moveSee >= 0 && USE_SEE_IN_QSEARCH)
					|| new_matgain >= 0
					//|| (env.getBitboard().isCheckMove(cur_move) && USE_CHECK_IN_QSEARCH)
					) {
				
				env.getBitboard().makeMoveForward(cur_move);
				
				if (env.getBitboard().isInCheck(colourToMove)) {
					if (allowIllegalMoves()) {
						env.getBitboard().makeMoveBackward(cur_move);
						continue;
					} else {
						throw new IllegalStateException();	
					}
				}
				legalMoves++;
				
				int cur_eval = -pv_qsearch(mediator, info, initial_maxdepth, depth + 1, -beta, -alpha, -new_matgain, 0, false, rootColour);
				
				env.getBitboard().makeMoveBackward(cur_move);
				
				if (cur_eval > best_eval) {
				//if ((inCheck && cur_eval > best_eval) || (!inCheck && cur_eval > alpha)) {
					best_eval = cur_eval;
					best_move = cur_move;
					
					if (best_eval > alpha) {
						
						node.bestmove = best_move;
						node.eval = best_eval;
						node.leaf = false;
						if (depth + 1 < MAX_DEPTH) {
							pvman.store(depth + 1, node, pvman.load(depth + 1), true);
						}
						
						alpha = best_eval;
					}
					
					if (best_eval >= beta) {
						
						if (inCheck) {
							env.getHistory_check().goodMove(cur_move, 1, best_eval > 0 && isMateVal(best_eval));
						} else {
						}
						
						break;
					}
				}
			}
			
		} while ((cur_move = list.next()) != 0);
		
		if (best_move == 0) {
			if (inCheck) {
				if (legalMoves == 0) {
					node.bestmove = 0;
					node.eval = -getMateVal(depth);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				} else {
					throw new IllegalStateException("!!" + env.getBitboard().toString());
				}
			} else {
				//All captures lead to evaluation which is less than the static eval
			}
		}
		
		if (!inCheck && staticEval > best_eval) {
			best_move = 0;
			best_eval = staticEval;
			
			node.leaf = true;
			node.eval = staticEval;
			node.bestmove = 0;
			node.nullmove = false;
		}
		
		if (allowTPTAccess(initial_maxdepth, depth)) {
			if (STORE_TPT_IN_QSEARCH && best_move != 0) {
				env.getTPT().lock();
				env.getTPT().put(hashkey, 0, 0, env.getBitboard().getColourToMove(), best_eval, alpha_org, beta, best_move, (byte)0);
				env.getTPT().unlock();
			}
		}
		
		return best_eval;
	}
	
	private int nullwin_qsearch(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth, int depth, int beta, int matgain, int initialStaticEval, boolean firstTime, int rootColour) {
		
		if (!firstTime) info.setSearchedNodes(info.getSearchedNodes() + 1);	
		
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		long hashkey = env.getBitboard().getHashKey();
		
		if (hashkey == -3770350413322162370L) {
			int g = 0;
		}
		
		
		int staticEval = firstTime ? initialStaticEval : lazyEval(depth, beta - 1, beta, rootColour);
		if (depth >= MAX_DEPTH) {
			return staticEval;
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		
		if (!firstTime) if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, beta - 1, beta);
		
		if (isDraw()) {
			return getDrawScores(rootColour);
		}
		
		boolean inCheck = env.getBitboard().isInCheck();
		
		int alpha_org = beta - 1;
	    // Mate distance pruning
		if (USE_MATE_DISTANCE && !inCheck && depth >= 1) {
			
		      /*if (inCheck && !env.getBitboard().hasMove()) {
					return -getMateVal(depth);
		      }*/
			
		      int value = -getMateVal(depth+2); // does not work if the current position is mate

		      if (value > alpha_org) {
		         if (value >= beta) {
					return value;
		         }
		      }

		      // upper bound

		      value = getMateVal(depth+1);

		      if (value < beta) {
		         beta = value;
		         if (value <= alpha_org) {
						return value;
		         }
		      }
		}
		
		if (!inCheck) {
			if (staticEval >= beta) {
				return staticEval;
			}
			
			if (!isMateVal(beta - 1)
					&& staticEval + env.getEval().getMaterialQueen() + 100 < beta - 1) {
				return staticEval;
			}
			
			if (staticEval > beta - 1) {
				throw new IllegalStateException();
			}
		}

		//int tpt_move = getTPTMove(hashkey);
		boolean tpt_found = false;
		boolean tpt_exact = false;
		int tpt_lower = MIN;
		int tpt_upper = MAX;
		int tpt_move = 0;
		
		if (allowTPTAccess(initial_maxdepth, depth)) {
			env.getTPT().lock();
			{
				TPTEntry tptEntry = env.getTPT().get(hashkey);
				if (tptEntry != null) {
					tpt_found = true;
					tpt_exact = tptEntry.isExact();
					tpt_lower = tptEntry.getLowerBound();
					tpt_upper = tptEntry.getUpperBound();
					tpt_move = tptEntry.getBestMove_lower();
				}
			}
			env.getTPT().unlock();
		}
		
		if (USE_TPT_SCORES && tpt_found) {
			if (tpt_exact) {
				if (!SearchUtils.isMateVal(tpt_lower)) {
					return tpt_lower;
				}
			} else {
				if (tpt_lower >= beta) {
					if (!SearchUtils.isMateVal(tpt_lower)) {
						return tpt_lower;
					}
				}
				if (tpt_upper <= beta - 1) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						return tpt_upper;
					}
				}
			}
		}
		
		ISearchMoveList list = null;
		if (inCheck) { 
			list = lists_escapes[depth];
			list.clear();
			list.setTptMove(tpt_move);
		} else {
			list = lists_capsproms[depth];
			list.clear();
			list.setTptMove(tpt_move);
		}
		
		int legalMoves = 0;
		int best_eval = MIN;
		int best_move = 0;
		int cur_move = 0;
		
		int alpha = beta - 1;
		
		if (inCheck) {
			cur_move = (tpt_move != 0) ? tpt_move : list.next();
		} else {
			cur_move = (tpt_move != 0 && MoveInt.isCaptureOrPromotion(tpt_move)) ? tpt_move : list.next();
		}
		
		int searchedMoves = 0;
		if (cur_move != 0) 
		do {
			
			if (searchedMoves > 0 && cur_move == tpt_move) {
				continue;
			}
			searchedMoves++;
			
			if (MoveInt.isCapture(cur_move)) {
				if (MoveInt.getCapturedFigureType(cur_move) == Figures.TYPE_KING) {
					throw new IllegalStateException();
				}
			}
			
			int new_matgain = matgain + env.getBitboard().getMaterialFactor().getMaterialGain(cur_move);
			
			//int moveSee = env.getBitboard().getSee().evalExchange(cur_move);
			
			if (inCheck
					//|| moveSee >= 0
					|| new_matgain >= 0
					//|| (env.getBitboard().isCheckMove(cur_move) && USE_CHECK_IN_QSEARCH)
					) {
				
				env.getBitboard().makeMoveForward(cur_move);
				
				if (env.getBitboard().isInCheck(colourToMove)) {
					if (allowIllegalMoves()) {
						env.getBitboard().makeMoveBackward(cur_move);
						continue;
					} else {
						throw new IllegalStateException();	
					}
				}
				legalMoves++;
				
				int cur_eval = -nullwin_qsearch(mediator, info, initial_maxdepth, depth + 1, -alpha, -new_matgain, 0, false, rootColour);
				
				env.getBitboard().makeMoveBackward(cur_move);
				
				if (cur_eval > best_eval) {
				//if ((inCheck && cur_eval > best_eval) || (!inCheck && cur_eval > alpha)) {
					best_eval = cur_eval;
					best_move = cur_move;

					if (best_eval >= beta) {
						break;
					}
					
					if (best_eval > alpha) {
						alpha = best_eval;
					}
				}
			}
			
		} while ((cur_move = list.next()) != 0);
		
		if (best_move == 0) {
			if (inCheck) {
				if (legalMoves == 0) {
					return -getMateVal(depth);
				} else {
					throw new IllegalStateException();
				}
			} else {
				//All captures lead to evaluation which is less than the static eval
			}
		}
		
		if (!inCheck && staticEval >= best_eval) {
			best_move = 0;
			best_eval = staticEval;
		}
		
		if (allowTPTAccess(initial_maxdepth, depth)) {
			if (STORE_TPT_IN_QSEARCH && best_move != 0) {
				env.getTPT().lock();
				env.getTPT().put(hashkey, 0, 0, env.getBitboard().getColourToMove(), best_eval, beta - 1, beta, best_move, (byte)0);
				env.getTPT().unlock();
			}
		}
		
		return best_eval;
	}
	
	
	protected int getTrustWindow() {
		return 0;
	}
}
