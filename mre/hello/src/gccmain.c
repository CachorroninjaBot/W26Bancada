#include <stdio.h>
#include "vmsys.h"
#include "vmchset.h"
#include "vmlog.h"
#include "vmio.h"
#include "vmstdlib.h"

extern unsigned int __init_array_start;
extern unsigned int __init_array_end;

typedef VMINT(*vm_get_sym_entry_t)(char* symbol);
vm_get_sym_entry_t vm_get_sym_entry;
typedef void (**__init_array) (void);

/* Cabecalho que o loader MRE 2.0 (ADS) escreve em RAM base 0:
   0x04 _saved_sp, 0x08 _init_param, 0x0c resolvedor vm_get_sym_entry.
   Reservamos para nao colidir com nossos dados e ler o resolvedor de 0x0c. */
__attribute__((section(".mre2_head"), used)) unsigned int _mre2_head[4];

int __g_errno = 0;

void* malloc(size_t size)
{
	return vm_malloc(size);
}

void* calloc(size_t number, size_t size)
{
	return vm_calloc(size * number);
}

void* realloc(void* ptr, size_t newsize) {
	return vm_realloc(ptr, newsize);
}

void free(void* ptr)
{
	vm_free(ptr);
}

void* _calloc_r(struct _reent* unused, size_t count, size_t size)
{
	(void)unused;
	void* ret = calloc(count, size);
	return ret;
}

//void __cxa_pure_virtual()
//{
//	while (1);
//}

//int __cxa_guard_acquire(int* p)
//{
//	return 1;
//}
//
//void __cxa_guard_release(int* p)
//{
//	return;
//}

//int* __errno()
//{
//	return &__g_errno;
//}

void gcc_entry(unsigned int entry, unsigned int init_array_start, unsigned int count)
{
	/* MRE 3.x passa o resolvedor em r0 e init_array em r1/r2.
	   MRE 2.0 (ADS) preenche os slots _vm_* por nome e/ou o resolvedor em 0x0c;
	   r1/r2 nao sao init_array -> ignorar e usar nossos proprios simbolos. */
	(void)init_array_start;
	(void)count;
	__init_array ptr, end;

	if (!vm_get_sym_entry)
		vm_get_sym_entry = *(vm_get_sym_entry_t*)&_mre2_head[3];
	if (!vm_get_sym_entry && entry)
		vm_get_sym_entry = (vm_get_sym_entry_t)entry;

	ptr = (__init_array)&__init_array_start;
	end = (__init_array)&__init_array_end;
	for (; ptr < end; ptr++)
		(*ptr)();
	vm_main();
}
