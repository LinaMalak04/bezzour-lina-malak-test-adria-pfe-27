import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Account, BankApiService, NewTransaction, NewTransfer, Transaction } from './bank-api.service';

type Tab = 'accounts' | 'payment' | 'transfer' | 'assistant';

@Component({
  selector: 'app-root',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private readonly api = inject(BankApiService);
  private readonly conversationId = crypto.randomUUID();

  readonly tab = signal<Tab>('accounts');
  readonly accounts = signal<Account[]>([]);
  readonly selected = signal<Account | null>(null);
  readonly transactions = signal<Transaction[]>([]);
  readonly notice = signal<{ kind: 'success' | 'error'; text: string } | null>(null);
  readonly chatLog = signal<{ from: 'user' | 'bot'; text: string }[]>([]);
  readonly waitingForBot = signal(false);
  readonly activeAccounts = computed(() => this.accounts().filter(account => account.status === 'ACTIVE'));

  payment: NewTransaction = { accountId: '', amount: 50, currency: 'EUR', merchantCategory: 'GROCERY', country: 'MA' };
  transferForm: NewTransfer = { sourceAccountId: '', targetAccountId: '', amount: 50, currency: 'EUR' };
  chatMessage = '';

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.api.accounts().subscribe({
      next: accounts => {
        this.accounts.set(accounts);
        const selected = this.selected();
        if (selected) {
          this.select(accounts.find(account => account.id === selected.id) ?? accounts[0]);
        }
      },
      error: error => this.showError(error)
    });
  }

  select(account: Account): void {
    this.selected.set(account);
    this.api.transactions(account.id).subscribe({
      next: transactions => this.transactions.set(transactions),
      error: error => this.showError(error)
    });
  }

  toggleStatus(account: Account): void {
    this.api.changeStatus(account.id, account.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE').subscribe({
      next: () => this.refresh(),
      error: error => this.showError(error)
    });
  }

  submitPayment(): void {
    this.api.createTransaction(this.payment).subscribe({
      next: transaction => this.afterOperation(transaction.status),
      error: error => this.showError(error)
    });
  }

  submitTransfer(): void {
    this.api.transfer(this.transferForm).subscribe({
      next: legs => this.afterOperation(legs[0].status),
      error: error => this.showError(error)
    });
  }

  sendChat(): void {
    const message = this.chatMessage.trim();
    if (!message || this.waitingForBot()) {
      return;
    }
    this.chatMessage = '';
    this.chatLog.update(log => [...log, { from: 'user', text: message }]);
    this.waitingForBot.set(true);
    this.api.chat(this.conversationId, message).subscribe({
      next: ({ reply }) => {
        this.chatLog.update(log => [...log, { from: 'bot', text: reply }]);
        this.waitingForBot.set(false);
        this.refresh();
      },
      error: () => {
        this.chatLog.update(log => [...log, { from: 'bot', text: "L'assistant est indisponible pour le moment." }]);
        this.waitingForBot.set(false);
      }
    });
  }

  private afterOperation(status: Transaction['status']): void {
    this.notice.set(status === 'PENDING'
      ? { kind: 'success', text: 'Opération enregistrée : le solde sera mis à jour dès le retour du service des comptes.' }
      : { kind: 'success', text: 'Opération effectuée.' });
    this.refresh();
  }

  private showError(error: HttpErrorResponse): void {
    this.notice.set({ kind: 'error', text: error.error?.detail ?? 'Le service est indisponible, réessayez dans un instant.' });
  }
}
