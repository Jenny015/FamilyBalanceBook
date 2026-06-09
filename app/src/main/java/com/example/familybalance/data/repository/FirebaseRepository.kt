package com.example.familybalance.data.repository

import com.example.familybalance.data.models.Entry
import com.example.familybalance.data.models.Meta
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class FirebaseRepository {

    private val database = FirebaseDatabase.getInstance().reference
    private val entriesRef = database.child("entries")
    private val metaRef = database.child("meta")

    // Interface to notify the UI when entries change
    interface DataCallback<T> {
        fun onSuccess(result: T)
        fun onError(error: DatabaseError)
    }

    /**
     * Fetches paginated data based on an endAt key (id).
     * Pulls 5 entries at a time moving backwards chronologically.
     */
    fun fetchEntriesPage(startFromId: Int?, limit: Int, callback: DataCallback<List<Entry>>) {
        var query: Query = entriesRef.orderByChild("id")

        if (startFromId != null) {
            // Query older items than the current oldest loaded ID
            query = query.endAt((startFromId - 1).toDouble())
        }

        query.limitToLast(limit).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Entry>()
                for (child in snapshot.children) {
                    val entry = child.getValue(Entry::class.java)
                    if (entry != null) list.add(entry)
                }
                // Return sorted in ascending order (Firebase outputs sorted order natively)
                callback.onSuccess(list)
            }

            override fun onCancelled(error: DatabaseError) {
                callback.onError(error)
            }
        })
    }

    /**
     * Real-time listener for the total running Balance calculation
     */
    fun listenToBalance(callback: (Double) -> Unit) {
        entriesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var netBalance = 0.0
                for (child in snapshot.children) {
                    val entry = child.getValue(Entry::class.java) ?: continue
                    if (entry.role == 0) {
                        netBalance += entry.amount  // Credit from Mom
                    } else {
                        netBalance -= entry.amount  // Debit from Daughter
                    }
                }
                callback(netBalance)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Writes or Updates an item, then updates the entry metadata count and runs maintenance
     */
    fun saveEntry(entry: Entry, isNew: Boolean, label: String, callback: () -> Unit) {
        if (isNew) {
            // Step 1: Run the transaction ONLY to update the ID counter securely
            metaRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    var meta = currentData.getValue(Meta::class.java)
                    if (meta == null) meta = Meta(0)

                    // Advance the counter safely
                    meta.totalEntries = meta.totalEntries + 1

                    currentData.value = meta
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (committed && snapshot != null) {
                        // Extract the final, confirmed unique ID from the meta snapshot
                        val updatedMeta = snapshot.getValue(Meta::class.java)
                        val nextId = updatedMeta?.totalEntries ?: 1

                        // Step 2: Now assign the ID and write the data SAFELY outside the transaction loop
                        entry.id = nextId
                        entriesRef.child(nextId.toString()).setValue(entry)
                            .addOnSuccessListener {
                                // Step 3: Run maintenance only after the write successfully hits the cloud
                                checkAndExecuteSummary(label)
                                callback()
                            }
                            .addOnFailureListener {
                                // Handle network/write failures gracefully
                            }
                    }
                }
            })
        } else {
            // Simply updating an existing node
            entriesRef.child(entry.id.toString()).setValue(entry).addOnCompleteListener {
                callback()
            }
        }
    }

    fun deleteEntry(entryId: Int, callback: () -> Unit, onError: (String) -> Unit) {
        // Force a string conversion to match your Firebase keys exactly
        val nodeKey = entryId.toString()

        entriesRef.child(nodeKey).removeValue()
            .addOnSuccessListener {
                callback()
            }
            .addOnFailureListener { exception ->
                // This will catch if Firebase rules are rejecting the deletion
                onError(exception.localizedMessage ?: "Unknown Firebase write error")
            }
    }

    /**
     * Core Maintenance Function: Triggers cleanup when count >= 30
     */
    private fun checkAndExecuteSummary(label: String) {
        entriesRef.orderByChild("id").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                // 1. STRICT GUARD: Total nodes MUST be 30 or more.
                // If it's 29 or less, stop immediately and do absolutely nothing.
                val actualCount = snapshot.childrenCount.toInt()
                if (actualCount < 30) {
                    return
                }

                val allEntries = mutableListOf<Entry>()
                for (child in snapshot.children) {
                    child.getValue(Entry::class.java)?.let { allEntries.add(it) }
                }

                // 2. DOUBLE CHECK: Ensure we actually have enough history to compress
                if (allEntries.size < 20) {
                    return
                }

                // Slice EXACTLY the 20 oldest items
                val summaryTargets = allEntries.take(20)

                var sumCredit = 0.0
                var sumDebit = 0.0
                val targetDate = summaryTargets.maxOf { it.date }

                for (entry in summaryTargets) {
                    if (entry.role == 0) sumCredit += entry.amount else sumDebit += entry.amount
                }

                val updates = hashMapOf<String, Any?>()

                // Queue deletion of ONLY these 20 targeted elements
                for (entry in summaryTargets) {
                    updates["entries/${entry.id}"] = null
                }

                // Recycle IDs safely
                val newCreditSummaryId = summaryTargets[0].id
                val newDebitSummaryId = summaryTargets[1].id

                val creditSummary = Entry(newCreditSummaryId, 0, targetDate, label, "", sumCredit)
                val debitSummary = Entry(newDebitSummaryId, 1, targetDate, label, "", sumDebit)

                updates["entries/$newCreditSummaryId"] = creditSummary
                updates["entries/$newDebitSummaryId"] = debitSummary

                // Execute automatically
                database.updateChildren(updates)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Add this inside FirebaseRepository
    fun getTotalEntryCount(onResult: (Int) -> Unit) {
        entriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.childrenCount.toInt()
                onResult(count)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(0) // Fallback if data read is canceled
            }
        })
    }
}