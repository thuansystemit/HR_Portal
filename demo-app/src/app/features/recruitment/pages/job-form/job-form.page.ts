import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, Validators } from '@angular/forms';
import { switchMap } from 'rxjs/operators';
import { SHARED_IMPORTS } from '../../../../shared/shared.imports';
import { DropdownOption } from '../../../../shared/components/dropdown/dropdown.model';
import { RecruitmentStore } from '../../store/recruitment.store';
import { RecruitmentApi } from '../../services/recruitment.api';
import { JobStatus } from '../../models/recruitment.model';

@Component({
  selector: 'app-job-form',
  imports: [...SHARED_IMPORTS],
  templateUrl: './job-form.page.html',
  styleUrl:    './job-form.page.scss',
})
export class JobFormPage implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb     = inject(FormBuilder);
  private readonly api    = inject(RecruitmentApi);
  protected readonly store = inject(RecruitmentStore);

  protected editId: string | null = null;
  protected get isEdit() { return this.editId !== null; }

  protected skillTags  = signal<string[]>([]);
  protected skillInput = signal('');

  protected form = this.fb.group({
    title:        ['', [Validators.required, Validators.minLength(2)]],
    department:   [''],
    location:     [''],
    description:  [''],
    requirements: [''],
    deadline:     [''],
    status:       ['' as JobStatus, Validators.required],
  });

  readonly statusOptions: DropdownOption[] = [
    { value: 'DRAFT', label: 'Draft' },
    { value: 'OPEN',  label: 'Open' },
    { value: 'CLOSED', label: 'Closed' },
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.editId = id;
      this.store.loadPosting(id);
      this.api.getPostingSkills(id).subscribe({
        next: skills => this.skillTags.set(skills.map(s => s.skillName)),
      });
    } else {
      this.store.clearCurrentPosting();
      this.form.patchValue({ status: 'DRAFT' });
    }
  }

  ngDoCheck(): void {
    const posting = this.store.currentPosting();
    if (this.isEdit && posting && !this.form.dirty) {
      this.form.patchValue({
        title:        posting.title,
        department:   posting.department || '',
        location:     posting.location || '',
        description:  posting.description || '',
        requirements: posting.requirements || '',
        deadline:     posting.deadline || '',
        status:       posting.status,
      });
    }
  }

  protected submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (this.skillInput().trim()) this.addSkillTag();

    const raw = this.form.getRawValue();
    const body = {
      title:        raw.title ?? '',
      department:   raw.department || undefined,
      location:     raw.location || undefined,
      description:  raw.description || undefined,
      requirements: raw.requirements || undefined,
      deadline:     raw.deadline || undefined,
      status:       raw.status ?? 'DRAFT',
    };
    const skills = this.skillTags().map(s => ({ skillName: s, isRequired: true }));

    if (this.isEdit) {
      this.api.updatePosting(this.editId!, body).pipe(
        switchMap(() => this.api.setPostingSkills(this.editId!, skills)),
      ).subscribe({
        next: () => this.router.navigate(['/recruitment']),
        error: err => this.store['_error'].set(err.error?.message || 'Update failed'),
      });
    } else {
      this.api.createPosting(body).pipe(
        switchMap(posting => this.api.setPostingSkills(posting.id, skills)),
      ).subscribe({
        next: () => this.router.navigate(['/recruitment']),
        error: err => this.store['_error'].set(err.error?.message || 'Create failed'),
      });
    }
  }

  protected isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  protected addSkillTag(): void {
    const val = this.skillInput().trim();
    if (val && !this.skillTags().includes(val)) {
      this.skillTags.update(tags => [...tags, val]);
    }
    this.skillInput.set('');
  }

  protected removeSkillTag(tag: string): void {
    this.skillTags.update(tags => tags.filter(t => t !== tag));
  }

  protected onSkillKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addSkillTag();
    }
  }
}
