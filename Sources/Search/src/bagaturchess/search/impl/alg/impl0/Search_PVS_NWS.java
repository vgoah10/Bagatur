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
import bagaturchess.bitboard.impl.Figures;
import bagaturchess.bitboard.impl.movegen.MoveInt;
import bagaturchess.egtb.gaviota.GTBProbeOutput;
import bagaturchess.egtb.syzygy.SyzygyConstants;
import bagaturchess.egtb.syzygy.SyzygyTBProbing;
import bagaturchess.search.api.internal.IRootWindow;
import bagaturchess.search.api.internal.ISearchInfo;
import bagaturchess.search.api.internal.ISearchMediator;
import bagaturchess.search.api.internal.ISearchMoveList;

import bagaturchess.search.impl.alg.BacktrackingInfo;
import bagaturchess.search.impl.env.SearchEnv;
import bagaturchess.search.impl.pv.PVNode;
import bagaturchess.search.impl.tpt.TPTEntry;
import bagaturchess.search.impl.utils.SearchUtils;


public class Search_PVS_NWS extends SearchImpl_MTD {
	
	
	private double LMR_REDUCTION_MULTIPLIER 		= 1;// * 1.222 * 1;
	private double NULL_MOVE_REDUCTION_MULTIPLIER 	= 1;// * 0.777 * 1;
	private double IID_DEPTH_MULTIPLIER 			= 1;
	private boolean STATIC_PRUNING1					= true;
	private boolean STATIC_PRUNING2 				= true;
	
	
	private BacktrackingInfo[] backtracking 		= new BacktrackingInfo[MAX_DEPTH + 1];
	
	private static final double EVAL_DIFF_MAX 		= 50;
	
	private long lastSentMinorInfo_timestamp;
	private long lastSentMinorInfo_nodesCount;
	
	
	public Search_PVS_NWS(Object[] args) {
		this(new SearchEnv((IBitBoard) args[0], getOrCreateSearchEnv(args)));
	}
	
	
	public Search_PVS_NWS(SearchEnv _env) {
		super(_env);
		for (int i=0; i<backtracking.length; i++) {
			backtracking[i] = new BacktrackingInfo(); 
		}
	}
	
	
	@Override
	public String toString() {
		String result = "";//"" + this + " ";
		
		result += Thread.currentThread().getName() + "	>	";
		result += getEnv().toString();
		
		return result;
	}
	
	
	public void newSearch() {
		
		super.newSearch();
		
		lastSentMinorInfo_nodesCount = 0;
		
		lastSentMinorInfo_timestamp = 0;
	}
	
	
	@Override
	public int pv_search(ISearchMediator mediator, IRootWindow rootWin,
			ISearchInfo info, int initial_maxdepth, int maxdepth, int depth,
			int alpha_org, int beta, int prevbest, int prevprevbest,
			int[] prevPV, boolean prevNullMove, int evalGain, int rootColour,
			int totalLMReduction, int materialGain, boolean inNullMove,
			int mateMove, boolean useMateDistancePrunning) {
		
		return this.pv_search(mediator, info, initial_maxdepth, maxdepth, depth,
				alpha_org, beta, prevNullMove, prevbest, prevprevbest, prevPV, rootColour,
				mateMove, useMateDistancePrunning, false, false);
	}


	@Override
	public int nullwin_search(ISearchMediator mediator, ISearchInfo info,
			int initial_maxdepth, int maxdepth, int depth, int beta,
			boolean prevNullMove, int prevbest, int prevprevbest, int[] prevPV,
			int rootColour, int totalLMReduction, int materialGain,
			boolean inNullMove, int mateMove, boolean useMateDistancePrunning) {
		
		return nullwin_search(mediator, info, initial_maxdepth, maxdepth, depth,
				beta, prevNullMove, prevbest, prevprevbest, prevPV, rootColour,
				mateMove, useMateDistancePrunning, false, false);
	}
	
	
	public int pv_search(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth,
			int maxdepth, int depth, int alpha_org, int beta, boolean prevNullMove, int prevbest, int prevprevbest,
			int[] prevPV, int rootColour,
			int mateMove, boolean useMateDistancePrunning, boolean useStaticPrunning, boolean useNullMove) {
		
		
		BacktrackingInfo backtrackingInfo = backtracking[depth];
		backtrackingInfo.hash_key = env.getBitboard().getHashKey();
		backtrackingInfo.static_eval = fullEval(depth, alpha_org, beta, rootColour);
		
		
		if (alpha_org >= beta) {
			throw new IllegalStateException("alpha=" + alpha_org + ", beta=" + beta);
		}
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		
		if (depth >= MAX_DEPTH) {
			return backtrackingInfo.static_eval;
		}
		
		if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, alpha_org, beta);
		
		long hashkey = env.getBitboard().getHashKey();
		
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
		if (!inCheck && useMateDistancePrunning && depth >= 1) {
		      
		      // lower bound
		      int value = -getMateVal(depth+2);
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
		
		
		int rest = normDepth(maxdepth) - depth;
		
		
		if (depth > 1
    	    	&& depth <= rest
    			&& SyzygyTBProbing.getSingleton() != null
    			&& SyzygyTBProbing.getSingleton().isAvailable(env.getBitboard().getMaterialState().getPiecesCount())
    			&& env.getBitboard().getColourToMove() == rootColour
    			){
			
			if (inCheck) {
				if (!env.getBitboard().hasMoveInCheck()) {
					node.bestmove = 0;
					node.eval = -getMateVal(depth);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				}
			} else {
				if (!env.getBitboard().hasMoveInNonCheck()) {
					node.bestmove = 0;
					node.eval = getDrawScores(rootColour);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				}
			}
			
			int result = SyzygyTBProbing.getSingleton().probeDTZ(env.getBitboard());
			if (result != -1) {
				int dtz = (result & SyzygyConstants.TB_RESULT_DTZ_MASK) >> SyzygyConstants.TB_RESULT_DTZ_SHIFT;
				int wdl = (result & SyzygyConstants.TB_RESULT_WDL_MASK) >> SyzygyConstants.TB_RESULT_WDL_SHIFT;
				int egtbscore =  SyzygyTBProbing.getSingleton().getWDLScore(wdl, depth);
				if (egtbscore > 0) {
					int distanceToDraw = 100 - env.getBitboard().getDraw50movesRule();
					if (distanceToDraw > dtz) {
						node.bestmove = 0;
						node.eval = 10 * (distanceToDraw - dtz);
						node.leaf = true;
						node.nullmove = false;
						return node.eval;
					} else {
						node.bestmove = 0;
						node.eval = getDrawScores(rootColour);
						node.leaf = true;
						node.nullmove = false;
						return node.eval;
					}
				} else if (egtbscore == 0) {
					node.bestmove = 0;
					node.eval = getDrawScores(rootColour);
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				}
			}
        }
		
		
		boolean disableExts = false;
		if (inCheck && rest < 1) {
			if (depth >= normDepth(maxdepth)) {
				maxdepth = PLY * (depth + 1);
				disableExts = true;
			}
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
					if (tpt_exact) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_lower >= beta) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_upper <= alpha_org) {
						tpt_move = tptEntry.getBestMove_upper();
					} else {
						tpt_move = tptEntry.getBestMove_lower();
						if (tpt_move == 0) {
							tpt_move = tptEntry.getBestMove_upper();
						}
					}
				}
			}
			env.getTPT().unlock();
		}
		
		if (backtrackingInfo.excluded_move == 0
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
					extractFromTPT(info, rest, node, true, buff_tpt_depthtracking, rootColour, env.getTPT());
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
						extractFromTPT(info, rest, node, true, buff_tpt_depthtracking, rootColour, env.getTPT());
						env.getTPT().unlock();
						
						if (buff_tpt_depthtracking[0] >= rest) {
							return node.eval;
						}
					}
				}
				if (tpt_upper <= alpha_org) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						node.bestmove = tpt_move;
						node.eval = tpt_upper;
						node.leaf = true;
						node.nullmove = false;
						
						
						env.getTPT().lock();
						buff_tpt_depthtracking[0] = 0;
						extractFromTPT(info, rest, node, false, buff_tpt_depthtracking, rootColour, env.getTPT());
						env.getTPT().unlock();
						
						if (buff_tpt_depthtracking[0] >= rest) {
							return node.eval;
						}
					}
				}
			}
		}
    	
    	
		if (depth >= normDepth(maxdepth)) {
			
			if (inCheck) {
				throw new IllegalStateException("inCheck: depth >= normDepth(maxdepth)");
			}
			
			node.eval = pv_qsearch(mediator, info, initial_maxdepth, depth, alpha_org, beta, rootColour);	
			return node.eval;
		}
		

        if (STATIC_PRUNING1 && useStaticPrunning
                ) {
                
            if (inCheck) {
                throw new IllegalStateException("In check in useStaticPrunning");
            }
        
            if (tpt_lower > TPTEntry.MIN_VALUE) {
                if (alpha_org > tpt_lower + getAlphaTrustWindow(mediator, rest) ) {
                    
                    node.eval = tpt_lower;
                    node.leaf = true;
                    node.nullmove = false;
                    
                    node.bestmove = 0;
                    env.getTPT().lock();
                    buff_tpt_depthtracking[0] = 0;
                    extractFromTPT(info, rest, node, true, buff_tpt_depthtracking, rootColour, env.getTPT());
                    env.getTPT().unlock();
                    
                    
                    return node.eval;
                }
            }
            
			
			if (alpha_org > backtrackingInfo.static_eval + getAlphaTrustWindow(mediator, rest)) {
                int qeval = pv_qsearch(mediator, info, initial_maxdepth, depth, alpha_org, beta, rootColour);
				if (alpha_org > qeval + getAlphaTrustWindow(mediator, rest) ) {
					node.bestmove = 0;
					node.eval = qeval;
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				}
			}
        }
		
        
		boolean hasAtLeastOnePiece = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getMaterialFactor().getWhiteFactor() >= 3 :
			env.getBitboard().getMaterialFactor().getBlackFactor() >= 3;

		boolean hasAtLeastThreePieces = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getMaterialFactor().getWhiteFactor() >= 9 :
			env.getBitboard().getMaterialFactor().getBlackFactor() >= 9;
		
		int new_mateMove = 0;
		boolean mateThreat = false;
		boolean zungzwang = false;
		if (useNullMove
				&& !inCheck
				&& !prevNullMove
				&& hasAtLeastOnePiece
				) {
						
			if (backtrackingInfo.static_eval >= beta) {
				
				//int null_reduction = PLY * (rest >= 6 ? 3 : 2);
				int null_reduction = PLY * (rest >= 6 ? 4 : 3);
				null_reduction = (int) (NULL_MOVE_REDUCTION_MULTIPLIER * Math.max(null_reduction, PLY * (rest / 2)));
				
				int null_maxdepth = maxdepth - null_reduction;
				
				env.getBitboard().makeNullMoveForward();
				int null_val = -nullwin_search(mediator, info,
						initial_maxdepth, null_maxdepth,
						depth + 1, -(beta - 1), true, prevprevbest, prevbest, prevPV, rootColour,
						0, useMateDistancePrunning, useStaticPrunning, useNullMove);
				
				if (null_val >= beta) {
					
					env.getBitboard().makeNullMoveBackward();
					
					if (hasAtLeastThreePieces) {
						return null_val;
					}
					
					int null_val_ver = nullwin_search(mediator, info, initial_maxdepth, null_maxdepth, depth,
							beta, prevNullMove, prevbest, prevprevbest, prevPV, rootColour,
							mateMove, useMateDistancePrunning, useStaticPrunning, useNullMove);
					
					if (null_val_ver >= beta) {
						return null_val_ver;
					} else {
						zungzwang = true;
						//System.out.println("zungzwang hit");
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
								if (tpt_exact) {
									tpt_move = tptEntry.getBestMove_lower();
								} else if (tpt_lower >= beta) {
									tpt_move = tptEntry.getBestMove_lower();
								} else if (tpt_upper <= alpha_org) {
									tpt_move = tptEntry.getBestMove_upper();
								} else {
									tpt_move = tptEntry.getBestMove_lower();
									if (tpt_move == 0) {
										tpt_move = tptEntry.getBestMove_upper();
									}
								}
							}
						}
						env.getTPT().unlock();
					}
				} else {
					if (backtrackingInfo.static_eval > alpha_org) { //PV node candidate
						if (null_val <= alpha_org) { //but bad thing appears
							if (null_val < 0 && isMateVal(null_val)) {//and the bad thing is mate
								mateThreat = true;
								
								if (allowTPTAccess(maxdepth, depth)) {
									env.getTPT().lock();
									TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
									if (entry != null) {
										new_mateMove = entry.getBestMove_lower();
									}
									env.getTPT().unlock();
								}
							}
						}
					}
					
					env.getBitboard().makeNullMoveBackward();
				}
			}
			
		}
		
		
		//IID PV Node
		if (!tpt_found && rest >= 6) {
			
			int reduction = (int) (IID_DEPTH_MULTIPLIER * Math.max(2, rest / 2));
			int iidRest = normDepth(maxdepth - PLY * reduction) - depth;
			
			if (tpt_depth < iidRest
				&& normDepth(maxdepth) - reduction > depth
				) {
				
				nullwin_search(mediator, info, initial_maxdepth, maxdepth - PLY * reduction, depth, beta,
						prevNullMove, prevbest, prevprevbest, prevPV, rootColour, mateMove, useMateDistancePrunning, useStaticPrunning, useNullMove);
				
				if (allowTPTAccess(maxdepth, depth)) {
					env.getTPT().lock();
					TPTEntry tptEntry = env.getTPT().get(env.getBitboard().getHashKey());
					if (tptEntry != null) {
						tpt_found = true;
						tpt_exact = tptEntry.isExact();
						tpt_depth = tptEntry.getDepth();
						tpt_lower = tptEntry.getLowerBound();
						tpt_upper = tptEntry.getUpperBound();
						if (tpt_exact) {
							tpt_move = tptEntry.getBestMove_lower();
						} else if (tpt_lower >= beta) {
							tpt_move = tptEntry.getBestMove_lower();
						} else if (tpt_upper <= alpha_org) {
							tpt_move = tptEntry.getBestMove_upper();
						} else {
							tpt_move = tptEntry.getBestMove_lower();
							if (tpt_move == 0) {
								tpt_move = tptEntry.getBestMove_upper();
							}
						}
					}
					env.getTPT().unlock();
				}
			}
		}
		
		
        if (STATIC_PRUNING1 && useStaticPrunning
                ) {
                
            if (inCheck) {
                throw new IllegalStateException("In check in useStaticPrunning");
            }
        
            if (tpt_lower > TPTEntry.MIN_VALUE) {
                if (alpha_org > tpt_lower + getAlphaTrustWindow(mediator, rest) ) {
                    
                    node.eval = tpt_lower;
                    node.leaf = true;
                    node.nullmove = false;
                    
                    node.bestmove = 0;
                    env.getTPT().lock();
                    buff_tpt_depthtracking[0] = 0;
                    extractFromTPT(info, rest, node, true, buff_tpt_depthtracking, rootColour, env.getTPT());
                    env.getTPT().unlock();
                    
                    
                    return node.eval;
                }
            }
        }
        
        
		//Singular move extension
		int singularExtension = 0;
		
		{
			env.getTPT().lock();
			TPTEntry tptEntry = env.getTPT().get(backtrackingInfo.hash_key);
			env.getTPT().unlock();
			
	        if (depth > 0
	        		&& rest >= 6//depth
	        		&& !disableExts
	        		&& backtracking[depth - 1].excluded_move == 0 //Skip recursive calls
	        		&& tptEntry != null
	        		//&& tptEntry.getDepth() >= rest - 3
	        		) {
	        	
		        boolean hasSingleMove = env.getBitboard().hasSingleMove();
		        
				if (hasSingleMove) {
					
					singularExtension = PLY;
					
				} else if (tptEntry.getBestMove_lower() != 0) {
						
					int ttValue = tptEntry.getLowerBound();
					
					int reduction = (PLY * rest) / 2;
					if (reduction >= PLY) {
						
						int singularBeta = ttValue - 2 * rest;
						
						backtrackingInfo.excluded_move = tptEntry.getBestMove_lower();
						int singularEval = nullwin_search(mediator, info, initial_maxdepth, maxdepth - PLY * reduction, depth, singularBeta,
								prevNullMove, prevbest, prevprevbest, prevPV, rootColour, mateMove, useMateDistancePrunning, useStaticPrunning, useNullMove);
						backtrackingInfo.excluded_move = 0;
						
						if (singularEval < singularBeta) {
							singularExtension = PLY;
							//System.out.println("singularExtension hit");
						}
					}
				}
	        }
		}
		
		
		node.bestmove = 0;
		node.eval = MIN;
		node.nullmove = false;
		node.leaf = true;
		
		
		double evalDiff = depth >= 2 ? backtrackingInfo.static_eval - backtracking[depth - 2].static_eval : 0;
		if (evalDiff > EVAL_DIFF_MAX) evalDiff = EVAL_DIFF_MAX;
		if (evalDiff < -EVAL_DIFF_MAX) evalDiff = -EVAL_DIFF_MAX;
		
		
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
		
		
		int searchedCount = 0;
		int legalMoves = 0;
		int alpha = alpha_org;
		int best_eval = MIN;
		int best_move = 0;
		
		
		int cur_move = (tpt_move != 0) ? tpt_move : list.next();
		if (cur_move != 0) {
			do {
				
				
				if (cur_move == backtrackingInfo.excluded_move) {
					continue;
				}
				
				
				if (searchedCount > 0 && cur_move == tpt_move) {
					continue;
				}
				
				
				//Build and sent minor info
				if (depth == 0) {
					info.setCurrentMove(cur_move);
					info.setCurrentMoveNumber((searchedCount + 1));
				}
				
				if (info.getSearchedNodes() >= lastSentMinorInfo_nodesCount + 50000 ) { //Check time on each 50 000 nodes
					
					long timestamp = System.currentTimeMillis();
					
					if (timestamp >= lastSentMinorInfo_timestamp + 1000)  {//Send info each second
					
						mediator.changedMinor(info);
						
						lastSentMinorInfo_timestamp = timestamp;
					}
					
					lastSentMinorInfo_nodesCount = info.getSearchedNodes();
				}
				
				
				boolean isCapOrProm = MoveInt.isCaptureOrPromotion(cur_move);
				int moveSee = -1;
				if (isCapOrProm) {
					moveSee = env.getBitboard().getSee().evalExchange(cur_move);
				}
				
				
				//Static pruning
				if (STATIC_PRUNING2 && !inCheck && !isCapOrProm && !env.getBitboard().isCheckMove(cur_move)) {
					
					if (searchedCount >= 4 && rest <= 8) {
						
						//Static pruning - move count based
						if (searchedCount >= 3 + Math.pow(rest, 2)) {
							continue;
						}
						
						//Static pruning - history based
						if (getHistory(inCheck).getScores(cur_move) <= 0.32 / Math.pow(2, rest)) {
	 						continue;
	 					}
						
						//Static pruning - evaluation based
						if (evalDiff < -(EVAL_DIFF_MAX - EVAL_DIFF_MAX / rest)) {
							continue;
						}
					}
				}
				
				env.getBitboard().makeMoveForward(cur_move);
				
				
				legalMoves++;
				
				
				boolean isCheckMove = env.getBitboard().isInCheck();
				
				
				int new_maxdepth = maxdepth;
				if (depth > 0 && !disableExts) {
					//Do extensions here
					if (cur_move == tpt_move) {
						new_maxdepth += singularExtension;
					} else if (zungzwang) {
						new_maxdepth += PLY;
					} else {
						/*if (evalDiff > 0) {
							new_maxdepth += PLY * (evalDiff / (double)EVAL_DIFF_MAX);	
						}
						new_maxdepth += PLY * getHistory(inCheck).getScores(cur_move);
						*/
					}
				}
				
				
				int cur_eval;
				if (searchedCount == 0) {
					
					cur_eval = -pv_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -beta, -alpha, false,
							best_move, prevbest, prevPV, rootColour,
							new_mateMove, useMateDistancePrunning, false, false);
				} else {
					
					boolean staticPrunning = false;
					
					if (!isCheckMove
							&& !isMateVal(alpha_org)
							&& !isMateVal(beta)
						) {
						staticPrunning = true;
					}
					
					int lmrReduction = 0;
					if (!inCheck
						 && !isCheckMove
						 //&& !mateThreat
						 //&& !isCapOrProm
						 && moveSee < 0
						 //&& rest > 3
						) {
						
						double rate = Math.max(1, Math.log(searchedCount) * Math.log(rest) / 2);
						rate += 2;//for pv nodes
						//rate *= (1 - getHistory(inCheck).getScores(cur_move));//In [0, 1]
						//rate *= (1 - (evalDiff / EVAL_DIFF_MAX));//In [0, 2]
						lmrReduction += (int) (PLY * rate * LMR_REDUCTION_MULTIPLIER);
					}
					int lmrRest = normDepth(maxdepth - lmrReduction) - depth - 1;
					if (lmrRest < 0) {
						lmrRest = 0;
					}
					
					
					cur_eval = -nullwin_search(mediator, info, initial_maxdepth,
							new_maxdepth - lmrReduction, depth + 1, -alpha, false,
							best_move, prevbest, prevPV, rootColour,
							new_mateMove, useMateDistancePrunning, staticPrunning, true);
					
					if (cur_eval > alpha && (lmrReduction > 0 || staticPrunning) ) {
						
						cur_eval = -nullwin_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -alpha, false,
								best_move, prevbest, prevPV, rootColour,
								new_mateMove, useMateDistancePrunning, false, true);
					}
					
					if (cur_eval > best_eval) {
						
						cur_eval = -pv_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -beta, -alpha, false,
								best_move, prevbest, prevPV, rootColour,
								new_mateMove, useMateDistancePrunning, false, false);
					}
				}
				
				
				env.getBitboard().makeMoveBackward(cur_move);
				
				//Add history records for the current move
				list.countTotal(cur_move);
				if (cur_eval < beta) {
					getHistory(inCheck).countFailure(cur_move, rest);
				} else {
					list.countSuccess(cur_move);//Should be before addCounterMove call
					getHistory(inCheck).countSuccess(cur_move, rest);
					getHistory(inCheck).addCounterMove(env.getBitboard().getLastMove(), cur_move);
				}
				
				if (cur_eval > best_eval) {
					
					best_eval = cur_eval;
					best_move = cur_move;
					
					node.bestmove = best_move;
					node.eval = best_eval;
					node.leaf = false;
					node.nullmove = false;
					
					if (depth + 1 < MAX_DEPTH) {
						pvman.store(depth + 1, node, pvman.load(depth + 1), true);
					}
					
					if (best_eval >= beta) {												
						break;
					}
					
					if (best_eval > alpha) {
						alpha = best_eval; 
						//throw new IllegalStateException();
					}
				}
				
				searchedCount++;
			} while ((cur_move = list.next()) != 0);
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
					node.eval = backtrackingInfo.static_eval;
					node.leaf = true;
					node.nullmove = false;
					return node.eval;
				}
			}
		}
		
		if (best_move == 0 || best_eval == MIN || best_eval == MAX) {
			throw new IllegalStateException();
		}
		
		
		if (backtrackingInfo.excluded_move == 0 && allowTPTAccess(maxdepth, depth)) {
			env.getTPT().lock();
			env.getTPT().put(hashkey, normDepth(maxdepth), depth, colourToMove, best_eval, alpha_org, beta, best_move, (byte)0);
			env.getTPT().unlock();
		}
		
		return best_eval;
	}
	
	
	public int nullwin_search(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth,
			int maxdepth, int depth, int beta,
			boolean prevNullMove, int prevbest, int prevprevbest, int[] prevPV, int rootColour, int mateMove,
			boolean useMateDistancePrunning, boolean useStaticPrunning, boolean useNullMove) {
		
		int alpha_org = beta - 1;
		
		BacktrackingInfo backtrackingInfo = backtracking[depth];
		backtrackingInfo.hash_key = env.getBitboard().getHashKey();
		backtrackingInfo.static_eval = lazyEval(depth, alpha_org, beta, rootColour);
		
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		
		
		if (depth >= MAX_DEPTH) {
			return backtrackingInfo.static_eval;
		}
		
		if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, alpha_org, beta);
				
		long hashkey = env.getBitboard().getHashKey();
		
		if (isDraw()) {
			return getDrawScores(rootColour);
		}
				
		
		boolean inCheck = env.getBitboard().isInCheck();
		
		
	    // Mate distance pruning
		if (!inCheck && useMateDistancePrunning && depth >= 1) {
		      
			  //lower bound
		      int value = -getMateVal(depth+2);
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
    	
		
		int rest = normDepth(maxdepth) - depth;
		
    	
		if (depth > 1
    			&& depth <= rest
    			&& SyzygyTBProbing.getSingleton() != null
    			&& SyzygyTBProbing.getSingleton().isAvailable(env.getBitboard().getMaterialState().getPiecesCount())
    			&& env.getBitboard().getColourToMove() == rootColour
    			){
			
			if (inCheck) {
				if (!env.getBitboard().hasMoveInCheck()) {
					return -getMateVal(depth);
				}
			} else {
				if (!env.getBitboard().hasMoveInNonCheck()) {
					return getDrawScores(rootColour);
				}
			}
			
			int result = SyzygyTBProbing.getSingleton().probeDTZ(env.getBitboard());
			if (result != -1) {
				int dtz = (result & SyzygyConstants.TB_RESULT_DTZ_MASK) >> SyzygyConstants.TB_RESULT_DTZ_SHIFT;
				int wdl = (result & SyzygyConstants.TB_RESULT_WDL_MASK) >> SyzygyConstants.TB_RESULT_WDL_SHIFT;
				int egtbscore =  SyzygyTBProbing.getSingleton().getWDLScore(wdl, depth);
				if (egtbscore > 0) {
					int distanceToDraw = 100 - env.getBitboard().getDraw50movesRule();
					if (distanceToDraw > dtz) {
						return 10 * (distanceToDraw - dtz);
					} else {
						return getDrawScores(rootColour);
					}
				} else if (egtbscore == 0) {
					return getDrawScores(rootColour);
				}
			}
		}
		
		
		boolean disableExts = false;
		if (inCheck && rest < 1) {
			if (depth >= normDepth(maxdepth)) {
				maxdepth = PLY * (depth + 1);
				disableExts = true;
			}
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
					if (tpt_exact) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_lower >= beta) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_upper <= alpha_org) {
						tpt_move = tptEntry.getBestMove_upper();
					} else {
						tpt_move = tptEntry.getBestMove_lower();
						if (tpt_move == 0) {
							tpt_move = tptEntry.getBestMove_upper();
						}
					}
				}
			}
			env.getTPT().unlock();
		}
		
		if (backtrackingInfo.excluded_move == 0
				&& tpt_found && tpt_depth >= rest) {
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
				if (tpt_upper <= alpha_org) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						return tpt_upper;
					}
				}
			}
		}
    	
		
		if (depth >= normDepth(maxdepth)) {
			
			if (inCheck) {
				throw new IllegalStateException();
			}
			
			int eval = nullwin_qsearch(mediator, info, initial_maxdepth, depth, beta, rootColour);
			return eval;
		}
		
		
        if (STATIC_PRUNING1 && useStaticPrunning
                ) {
                
            if (inCheck) {
                throw new IllegalStateException("In check in useStaticPrunning");
            }
        
            if (tpt_lower > TPTEntry.MIN_VALUE) {
                if (alpha_org > tpt_lower + getAlphaTrustWindow(mediator, rest) ) {
                    return tpt_lower;
                }
            }
            
            
			if (alpha_org > backtrackingInfo.static_eval + getAlphaTrustWindow(mediator, rest)) {
				int qeval = nullwin_qsearch(mediator, info, initial_maxdepth, depth, beta, rootColour);
				if (alpha_org > qeval + getAlphaTrustWindow(mediator, rest) ) {
					return qeval;
				}
			}
        }
        
        
		boolean hasAtLeastOnePiece = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getMaterialFactor().getWhiteFactor() >= 3 :
			env.getBitboard().getMaterialFactor().getBlackFactor() >= 3;
		
		boolean hasAtLeastThreePieces = (colourToMove == Figures.COLOUR_WHITE) ? env.getBitboard().getMaterialFactor().getWhiteFactor() >= 9 :
			env.getBitboard().getMaterialFactor().getBlackFactor() >= 9;
		
		int new_mateMove = 0;
		boolean mateThreat = false;
		boolean zungzwang = false;
		if (useNullMove
				&& !inCheck
				&& !prevNullMove
				&& hasAtLeastOnePiece
				) {
						
			if (backtrackingInfo.static_eval >= beta) {
				
				//int null_reduction = PLY * (rest >= 6 ? 3 : 2);
				int null_reduction = PLY * (rest >= 6 ? 4 : 3);
				null_reduction = (int) (NULL_MOVE_REDUCTION_MULTIPLIER * Math.max(null_reduction, PLY * (rest / 2)));
				
				int null_maxdepth = maxdepth - null_reduction;
				
				env.getBitboard().makeNullMoveForward();
				int null_val = -nullwin_search(mediator, info,
						initial_maxdepth, null_maxdepth,
						depth + 1, -(beta - 1), true, prevprevbest, prevbest, prevPV, rootColour,
						0, useMateDistancePrunning, useStaticPrunning, useNullMove);
				
				if (null_val >= beta) {
					
					env.getBitboard().makeNullMoveBackward();
					
					if (hasAtLeastThreePieces) {
						return null_val;
					}
					
					int null_val_ver = nullwin_search(mediator, info, initial_maxdepth, null_maxdepth, depth,
							beta, prevNullMove, prevbest, prevprevbest, prevPV, rootColour,
							mateMove, useMateDistancePrunning, useStaticPrunning, useNullMove);
					
					if (null_val_ver >= beta) {
						return null_val_ver;
					} else {
						zungzwang = true;
						//System.out.println("zungzwang hit");
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
								if (tpt_exact) {
									tpt_move = tptEntry.getBestMove_lower();
								} else if (tpt_lower >= beta) {
									tpt_move = tptEntry.getBestMove_lower();
								} else if (tpt_upper <= alpha_org) {
									tpt_move = tptEntry.getBestMove_upper();
								} else {
									tpt_move = tptEntry.getBestMove_lower();
									if (tpt_move == 0) {
										tpt_move = tptEntry.getBestMove_upper();
									}
								}
							}
						}
						env.getTPT().unlock();
					}
				} else {
					if (backtrackingInfo.static_eval > alpha_org) { //PV node candidate
						if (null_val <= alpha_org) { //but bad thing appears
							if (null_val < 0 && isMateVal(null_val)) {//and the bad thing is mate
								mateThreat = true;
								
								if (allowTPTAccess(maxdepth, depth)) {
									env.getTPT().lock();
									TPTEntry entry = env.getTPT().get(env.getBitboard().getHashKey());
									if (entry != null) {
										new_mateMove = entry.getBestMove_lower();
									}
									env.getTPT().unlock();
								}
							}
						}
					}
					
					env.getBitboard().makeNullMoveBackward();
				}
			}
		}
		
		
		//IID NONPV Node
		if (!tpt_found && rest >= 6) {
			
			int reduction = (int) (IID_DEPTH_MULTIPLIER * Math.max(2, rest / 2));
			int iidRest = normDepth(maxdepth - PLY * reduction) - depth;
			
			if (tpt_depth < iidRest
					&& normDepth(maxdepth) - reduction > depth) {
				
				nullwin_search(mediator, info, initial_maxdepth, maxdepth - PLY * reduction, depth, beta,
						prevNullMove, prevbest, prevprevbest, prevPV, rootColour,
						mateMove, useMateDistancePrunning, useStaticPrunning, useNullMove);
				
				if (allowTPTAccess(maxdepth, depth)) {
					env.getTPT().lock();
					TPTEntry tptEntry = env.getTPT().get(env.getBitboard().getHashKey());
					if (tptEntry != null) {
						tpt_found = true;
						tpt_exact = tptEntry.isExact();
						tpt_depth = tptEntry.getDepth();
						tpt_lower = tptEntry.getLowerBound();
						tpt_upper = tptEntry.getUpperBound();
						if (tpt_exact) {
							tpt_move = tptEntry.getBestMove_lower();
						} else if (tpt_lower >= beta) {
							tpt_move = tptEntry.getBestMove_lower();
						} else if (tpt_upper <= alpha_org) {
							tpt_move = tptEntry.getBestMove_upper();
						} else {
							tpt_move = tptEntry.getBestMove_lower();
							if (tpt_move == 0) {
								tpt_move = tptEntry.getBestMove_upper();
							}
						}
					}
					env.getTPT().unlock();
				}
				
			}
		}
		
		
        if (STATIC_PRUNING1 && useStaticPrunning
                ) {
                
            if (inCheck) {
                throw new IllegalStateException("In check in useStaticPrunning");
            }
        
            if (tpt_lower > TPTEntry.MIN_VALUE) {
                if (alpha_org > tpt_lower + getAlphaTrustWindow(mediator, rest) ) {
                    return tpt_lower;
                }
            }
        }
        
        
		//Singular move extension
		int singularExtension = 0;
		
		{
			env.getTPT().lock();
			TPTEntry tptEntry = env.getTPT().get(backtrackingInfo.hash_key);
			env.getTPT().unlock();
			
	        if (depth > 0
	        		&& rest >= 6//depth
	        		&& !disableExts
	        		&& backtracking[depth - 1].excluded_move == 0 //Skip recursive calls
	        		&& tptEntry != null
	        		//&& tptEntry.getDepth() >= rest - 3
	        		) {
	        	
		        boolean hasSingleMove = env.getBitboard().hasSingleMove();
		        
				if (hasSingleMove) {
					
					singularExtension = PLY;
					
				} else if (tptEntry.getBestMove_lower() != 0) {
						
					int ttValue = tptEntry.getLowerBound();
					
					int reduction = (PLY * rest) / 2;
					if (reduction >= PLY) {
						
						int singularBeta = ttValue - 2 * rest;
						
						backtrackingInfo.excluded_move = tptEntry.getBestMove_lower();
						int singularEval = nullwin_search(mediator, info, initial_maxdepth, maxdepth - PLY * reduction, depth, singularBeta,
								prevNullMove, prevbest, prevprevbest, prevPV, rootColour, mateMove, useMateDistancePrunning, useStaticPrunning, useNullMove);
						backtrackingInfo.excluded_move = 0;
						
						if (singularEval < singularBeta) {
							singularExtension = PLY;
							//System.out.println("singularExtension hit");
						}
					}
				}
	        }
		}
		
		
		double evalDiff = depth >= 2 ? backtrackingInfo.static_eval - backtracking[depth - 2].static_eval : 0;
		if (evalDiff > EVAL_DIFF_MAX) evalDiff = EVAL_DIFF_MAX;
		if (evalDiff < -EVAL_DIFF_MAX) evalDiff = -EVAL_DIFF_MAX;
		
		
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
		
		
		int searchedCount = 0;
		int legalMoves = 0;
		int best_eval = MIN;
		int best_move = 0;
		
		int cur_move = (tpt_move != 0) ? tpt_move : list.next();
		if (cur_move != 0) {
			do {
				
				
				if (cur_move == backtrackingInfo.excluded_move) {
					continue;
				}
				
				
				if (searchedCount > 0 && cur_move == tpt_move) {
					continue;
				}
				
				
				//Build and sent minor info
				if (depth == 0) {
					info.setCurrentMove(cur_move);
					info.setCurrentMoveNumber((searchedCount + 1));
				}
				
				if (info.getSearchedNodes() >= lastSentMinorInfo_nodesCount + 50000 ) { //Check time on each 50 000 nodes
					
					long timestamp = System.currentTimeMillis();
					
					if (timestamp >= lastSentMinorInfo_timestamp + 1000)  {//Send info each second
					
						mediator.changedMinor(info);
						
						lastSentMinorInfo_timestamp = timestamp;
					}
					
					lastSentMinorInfo_nodesCount = info.getSearchedNodes();
				}
				
				
				boolean isCapOrProm = MoveInt.isCaptureOrPromotion(cur_move);
				int moveSee = -1;
				if (isCapOrProm) {
					moveSee = env.getBitboard().getSee().evalExchange(cur_move);
				}
				
				
				//Static pruning
				if (STATIC_PRUNING2 && !inCheck && !isCapOrProm && !env.getBitboard().isCheckMove(cur_move)) {
					
					if (searchedCount >= 4 && rest <= 8) {
						
						//Static pruning - move count based
						if (searchedCount >= 3 + Math.pow(rest, 2)) {
							continue;
						}
						
						//Static pruning - history based
						if (getHistory(inCheck).getScores(cur_move) <= 0.32 / Math.pow(2, rest)) {
	 						continue;
	 					}
						
						//Static pruning - evaluation based
						if (evalDiff < -(EVAL_DIFF_MAX - EVAL_DIFF_MAX / rest)) {
							continue;
						}
					}
				}
				
				env.getBitboard().makeMoveForward(cur_move);
				
				
				legalMoves++;
				
				
				boolean isCheckMove = env.getBitboard().isInCheck();
				
				
				int new_maxdepth = maxdepth;
				if (depth > 0 && !disableExts) {
					//Do extensions here
					if (cur_move == tpt_move) {
						new_maxdepth += singularExtension;
					} else if (zungzwang) {
						new_maxdepth += PLY;
					} else {
						/*if (evalDiff > 0) {
							new_maxdepth += PLY * (evalDiff / (double)EVAL_DIFF_MAX);	
						}
						new_maxdepth += PLY * getHistory(inCheck).getScores(cur_move);
						*/
					}
				}
				
				
				int cur_eval;
				if (searchedCount == 0) {
					
					cur_eval = -nullwin_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -alpha_org, false,
							best_move, prevbest, prevPV, rootColour,
							new_mateMove, useMateDistancePrunning, false, true);
				} else {
					
					boolean staticPrunning = false;
					
					if (!isCheckMove
							&& !isMateVal(alpha_org)
							&& !isMateVal(beta)
						) {
						staticPrunning = true;
					}
					
					int lmrReduction = 0;
					if (!inCheck
						 && !isCheckMove
						 //&& !mateThreat
						 //&& !isCapOrProm
						 && moveSee < 0
						 //&& rest > 3
						) {
						
						double rate = Math.max(1, Math.log(searchedCount) * Math.log(rest) / 2);
						rate += 2;//for non pv nodes
						//rate *= (1 - getHistory(inCheck).getScores(cur_move));//In [0, 1]
						//rate *= (1 - (evalDiff / EVAL_DIFF_MAX));//In [0, 2]
						lmrReduction += (int) (PLY * rate * LMR_REDUCTION_MULTIPLIER);
					}
					int lmrRest = normDepth(maxdepth - lmrReduction) - depth - 1;
					if (lmrRest < 0) {
						lmrRest = 0;
					}
					
					
					cur_eval = -nullwin_search(mediator, info, initial_maxdepth,
							new_maxdepth - lmrReduction, depth + 1, -alpha_org, false,
							best_move, prevbest, prevPV, rootColour,
							new_mateMove, useMateDistancePrunning, staticPrunning, true);
					
					if (cur_eval > alpha_org && (lmrReduction > 0 || staticPrunning) ) {
						
						cur_eval = -nullwin_search(mediator, info, initial_maxdepth, new_maxdepth, depth + 1, -alpha_org, false,
								best_move, prevbest, prevPV, rootColour,
								new_mateMove, useMateDistancePrunning, false, true);
					}
				}
						
						
				env.getBitboard().makeMoveBackward(cur_move);
				
				//Add history records for the current move
				list.countTotal(cur_move);
				if (cur_eval < beta) {
					getHistory(inCheck).countFailure(cur_move, rest);
				} else {
					list.countSuccess(cur_move);//Should be before addCounterMove call
					getHistory(inCheck).countSuccess(cur_move, rest);
					getHistory(inCheck).addCounterMove(env.getBitboard().getLastMove(), cur_move);
				}
				
				if (cur_eval > best_eval) {
					
					best_eval = cur_eval;
					best_move = cur_move;
					
					if (best_eval >= beta) {
						break;
					}
					
					if (best_eval > alpha_org) {
						throw new IllegalStateException(); 
					}
				}
				
				searchedCount++;
			} while ((cur_move = list.next()) != 0);
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
					return backtrackingInfo.static_eval;
				}
			}
		}
		
		
		if (best_move == 0 || best_eval == MIN || best_eval == MAX) {
			throw new IllegalStateException();
		}
		
		
		if (backtrackingInfo.excluded_move == 0 && allowTPTAccess(maxdepth, depth)) {
			env.getTPT().lock();
			env.getTPT().put(hashkey, normDepth(maxdepth), depth, colourToMove, best_eval, alpha_org, beta, best_move, (byte)0);
			env.getTPT().unlock();
		}
		
		return best_eval;

	}
	
	
	private int pv_qsearch(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth, int depth, int alpha_org, int beta, int rootColour) {
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);	
		
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}

		if (depth >= MAX_DEPTH) {
			return fullEval(depth, alpha_org, beta, rootColour);
		}
		
		
		int colourToMove = env.getBitboard().getColourToMove();
		
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
		
		
		int staticEval = -1;
		if (!inCheck) {
			staticEval = lazyEval(depth, alpha_org, beta, rootColour);
			
			if (staticEval >= beta) {
				staticEval = fullEval(depth, alpha_org, beta, rootColour);
			}
		}
		
		
	    // Mate distance pruning
		if (!inCheck && depth >= 1) {
				
		      // lower bound
		      int value = -getMateVal(depth+2);
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
		
		
		long hashkey = env.getBitboard().getHashKey();
		
		
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
					tpt_exact = tptEntry.isExact();
					tpt_lower = tptEntry.getLowerBound();
					tpt_upper = tptEntry.getUpperBound();
					if (tpt_exact) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_lower >= beta) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_upper <= alpha_org) {
						tpt_move = tptEntry.getBestMove_upper();
					} else {
						tpt_move = tptEntry.getBestMove_lower();
						if (tpt_move == 0) {
							tpt_move = tptEntry.getBestMove_upper();
						}
					}
				}
			}
			env.getTPT().unlock();
		}
		
		if (tpt_found) {
			if (tpt_exact) {
				if (!SearchUtils.isMateVal(tpt_lower)) {
					node.bestmove = tpt_move;
					node.eval = tpt_lower;
					node.leaf = true;
					node.nullmove = false;
					
					env.getTPT().lock();
					buff_tpt_depthtracking[0] = 0;
					extractFromTPT(info, 0, node, true, buff_tpt_depthtracking, rootColour, env.getTPT());
					env.getTPT().unlock();
					
					if (buff_tpt_depthtracking[0] >= 0) {
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
						extractFromTPT(info, 0, node, true, buff_tpt_depthtracking, rootColour, env.getTPT());
						env.getTPT().unlock();
						
						if (buff_tpt_depthtracking[0] >= 0) {
							return node.eval;
						}
					}
				}
				if (tpt_upper <= alpha_org) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						node.bestmove = tpt_move;
						node.eval = tpt_upper;
						node.leaf = true;
						node.nullmove = false;
						
						env.getTPT().lock();
						buff_tpt_depthtracking[0] = 0;
						extractFromTPT(info, 0, node, false, buff_tpt_depthtracking, rootColour, env.getTPT());
						env.getTPT().unlock();
						
						if (buff_tpt_depthtracking[0] >= 0) {
							return node.eval;
						}
					}
				}
			}
		}
		
		
		if (!inCheck) {
			
			//Beta cutoff
			if (staticEval >= beta) {
				node.eval = staticEval;
				return node.eval;
			}
			
			//Alpha cutoff
			if (!isMateVal(alpha_org)
					&& !isMateVal(beta)
					&& staticEval + env.getBitboard().getBaseEvaluation().getMaterial(Figures.TYPE_QUEEN) + getAlphaTrustWindow(mediator, 1) < alpha_org) {
				node.eval = staticEval;
				return node.eval;
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
			cur_move = (tpt_move != 0) ? tpt_move : list.next();
		}
		
		
		int searchedMoves = 0;
		if (cur_move != 0) 
		do {
			
			if (searchedMoves > 0 && cur_move == tpt_move) {
				continue;
			}
			searchedMoves++;
			
				
			env.getBitboard().makeMoveForward(cur_move);
			
			
			legalMoves++;
			
			int cur_eval = -pv_qsearch(mediator, info, initial_maxdepth, depth + 1, -beta, -alpha, rootColour);
			
			env.getBitboard().makeMoveBackward(cur_move);
			
			if (cur_eval > best_eval) {
				
				best_eval = cur_eval;
				best_move = cur_move;
				
				node.bestmove = best_move;
				node.eval = best_eval;
				node.leaf = false;
				node.nullmove = false;
				
				if (depth + 1 < MAX_DEPTH) {
					pvman.store(depth + 1, node, pvman.load(depth + 1), true);
				}
				
				if (best_eval >= beta) {						
					break;
				}
				
				if (best_eval > alpha) {
					alpha = best_eval;
					//throw new IllegalStateException();
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
			if (best_move != 0) {
				env.getTPT().lock();
				env.getTPT().put(hashkey, 0, 0, env.getBitboard().getColourToMove(), best_eval, alpha_org, beta, best_move, (byte)0);
				env.getTPT().unlock();
			}
		}
		
		return best_eval;
	}
	
	private int nullwin_qsearch(ISearchMediator mediator, ISearchInfo info, int initial_maxdepth, int depth, int beta, int rootColour) {
		
		info.setSearchedNodes(info.getSearchedNodes() + 1);	
		
		if (info.getSelDepth() < depth) {
			info.setSelDepth(depth);
		}
		
		int alpha_org = beta - 1;
		
		if (depth >= MAX_DEPTH) {
			return lazyEval(depth, alpha_org, beta, rootColour);
		}
		
		int colourToMove = env.getBitboard().getColourToMove();
		
		if (mediator != null && mediator.getStopper() != null) mediator.getStopper().stopIfNecessary(normDepth(initial_maxdepth), colourToMove, alpha_org, beta);
		
		if (isDraw()) {
			return getDrawScores(rootColour);
		}
				
		
		boolean inCheck = env.getBitboard().isInCheck();
		
		int staticEval = -1;
		if (!inCheck) {
			staticEval = lazyEval(depth, alpha_org, beta, rootColour);
		}
		
	    // Mate distance pruning
		if (!inCheck && depth >= 1) {
				
			//lower bound
			int value = -getMateVal(depth+2);
			if (value > alpha_org) {
				if (value >= beta) {
					return value;
				}
			}
			  
			//upper bound
			value = getMateVal(depth+1);
			if (value < beta) {
			   beta = value;
			   if (value <= alpha_org) {
				   return value;
			   }
			}
		}
		
		
		long hashkey = env.getBitboard().getHashKey();
		
		
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
					if (tpt_exact) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_lower >= beta) {
						tpt_move = tptEntry.getBestMove_lower();
					} else if (tpt_upper <= alpha_org) {
						tpt_move = tptEntry.getBestMove_upper();
					} else {
						tpt_move = tptEntry.getBestMove_lower();
						if (tpt_move == 0) {
							tpt_move = tptEntry.getBestMove_upper();
						}
					}
				}
			}
			env.getTPT().unlock();
		}
		
		if (tpt_found) {
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
				if (tpt_upper <= alpha_org) {
					if (!SearchUtils.isMateVal(tpt_upper)) {
						return tpt_upper;
					}
				}
			}
		}
		
		
		if (!inCheck) {
			
			//Beta cutoff
			if (staticEval >= beta) {
				return staticEval;
			}
			
			//Alpha cutoff
			if (!isMateVal(alpha_org)
					&& !isMateVal(beta)
					&& staticEval + env.getBitboard().getBaseEvaluation().getMaterial(Figures.TYPE_QUEEN) + getAlphaTrustWindow(mediator, 1) < alpha_org) {
				return staticEval;
			}
			
			if (!inCheck && staticEval > alpha_org) {
				throw new IllegalStateException("!inCheck && staticEval > alpha - 1");
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
			cur_move = (tpt_move != 0) ? tpt_move : list.next();
		}
		
		
		int searchedMoves = 0;
		if (cur_move != 0) 
		do {
			
			if (searchedMoves > 0 && cur_move == tpt_move) {
				continue;
			}
			searchedMoves++;
			
			
			env.getBitboard().makeMoveForward(cur_move);
			
			
			legalMoves++;
			
			int cur_eval = -nullwin_qsearch(mediator, info, initial_maxdepth, depth + 1, -alpha, rootColour);
			
			env.getBitboard().makeMoveBackward(cur_move);
			
			if (cur_eval > best_eval) {
				
				best_eval = cur_eval;
				best_move = cur_move;

				if (best_eval >= beta) {
					break;
				}
				
				if (best_eval > alpha) {
					throw new IllegalStateException("best_eval > alpha");
				}
			}
			
		} while ((cur_move = list.next()) != 0);
		
		if (best_move == 0) {
			if (inCheck) {
				if (legalMoves == 0) {
					return -getMateVal(depth);
				} else {
					throw new IllegalStateException("best_move == 0 && legalMoves != 0");
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
			if (best_move != 0) {
				env.getTPT().lock();
				env.getTPT().put(hashkey, 0, 0, env.getBitboard().getColourToMove(), best_eval, alpha_org, beta, best_move, (byte)0);
				env.getTPT().unlock();
			}
		}
		
		return best_eval;
	}
	
	
	private boolean allowTPTAccess(int maxdepth, int depth) {
		return true;
	}
	
	
	private double getAlphaTrustWindow(ISearchMediator mediator, int rest) {
		
		//int DEPTH1_INTERVAL = 100;
		//int DEPTH1_INTERVAL = (int) (move_eval_diff.getDisperse());
		//int DEPTH1_INTERVAL = (int) (move_eval_diff.getEntropy());
		//int DEPTH1_INTERVAL = (int) ((move_eval_diff.getEntropy() + move_eval_diff.getDisperse()) / 2);
		
		//return 32;//Math.max(33,  DEPTH1_INTERVAL * (rest / (double) 2));
		//return Math.max(1,  DEPTH1_INTERVAL * (rest / (double) 2));
		
		//return 1 * Math.max(1, (rest / (double) 2 )) * mediator.getTrustWindow_AlphaAspiration();
		return 1 * mediator.getTrustWindow_AlphaAspiration();
	}
}
