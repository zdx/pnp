package org.allenai.pnp

import com.jayantkrish.jklol.util.KbestQueue
import com.jayantkrish.jklol.training.LogFunction

sealed trait PpSearchQueue[A] {
  val graph: CompGraph
  val stateCost: Env => Double
  val log: LogFunction

  def offer(value: Pp[A], env: Env, logProb: Double, myEnv: Env): Unit
}

class BeamPpSearchQueue[A](size: Int, val stateCost: Env => Double,
    val graph: CompGraph, val log: LogFunction) extends PpSearchQueue[A] {

  /*
  val supplier = new Supplier[SearchState2]() {
    def get: SearchState2 = {
      new SearchState2(null, null, 0.0, null)
    }
  }
  val pool = new ObjectPool(supplier, size + 1, Array.empty[SearchState2])
  */
  
  val queue = new KbestQueue(size, Array.empty[SearchState[A]])

  override def offer(value: Pp[A], env: Env, logProb: Double, myEnv: Env): Unit = {
    val stateLogProb = stateCost(env) + logProb
    if (stateLogProb > Double.NegativeInfinity) {
      /*
      val next = pool.alloc()
      next.value = value
      next.env = env
      next.continuation = continuation
      next.logProb = logProb
      val dequeued = queue.offer(next, logProb)

      if (dequeued != null) {
        pool.dealloc(dequeued)
      }
      */
      queue.offer(SearchState(value, env, stateLogProb), logProb)
    }
  }
}

class EnumeratePpSearchQueue[A] (
    val stateCost: Env => Double,
    val graph: CompGraph, val log: LogFunction,
    val finished: PpSearchQueue[A]
) extends PpSearchQueue[A] {
  override def offer(value: Pp[A], env: Env, logProb: Double, myEnv: Env): Unit = {
    myEnv.pauseTimers()
    val stateLogProb = stateCost(env) + logProb
    if (stateLogProb > Double.NegativeInfinity) {
      env.resumeTimers()
      value.lastSearchStep(env, logProb, this, finished)
      env.pauseTimers()
    }
    myEnv.resumeTimers()
  }
}

class ContinuationPpSearchQueue[A, B] (
    val queue: PpSearchQueue[B],
    val cont: PpContinuation[A,B]
) extends PpSearchQueue[A] {
  
  val graph = queue.graph
  val stateCost = queue.stateCost
  val log = queue.log
  
  override def offer(value: Pp[A], env: Env, logProb: Double, myEnv: Env): Unit = {
    queue.offer(BindPp(value, cont), env, logProb, myEnv)
  }
}

case class SearchState[A](val value: Pp[A], val env: Env, val logProb: Double) {
}
